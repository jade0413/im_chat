//! P2-4：从反向代理转发头还原真实客户端 IP。
//!
//! im-web 的 nginx 反代 `/ws` 时，网关看到的 peer 是代理容器 IP；若直接用 peer
//! 做 per-IP 握手限流，所有 Web 用户会被算进同一只桶（默认 20/s），网关重启后的
//! Web 端重连风暴会被大面积 429。
//!
//! 安全前提：**仅当直连 peer 位于 trusted CIDR 内**才信任 `X-Forwarded-For` /
//! `X-Real-IP`，防止直连客户端伪造转发头绕过限流。trusted 列表默认为空（谁都不信）。

use axum::http::HeaderMap;
use std::net::IpAddr;

/// 极简 CIDR（v4/v6），仅用于 trusted proxy 匹配，不值得为此引入 ipnet 依赖。
#[derive(Clone, Debug)]
pub struct IpNet {
    addr: IpAddr,
    prefix_len: u8,
}

impl IpNet {
    /// 解析 `10.0.0.0/8` 或裸 IP（等价 /32、/128）。非法输入返回 None。
    pub fn parse(input: &str) -> Option<Self> {
        let input = input.trim();
        let (addr, prefix_len) = match input.split_once('/') {
            Some((ip, len)) => (ip.trim().parse::<IpAddr>().ok()?, len.trim().parse().ok()?),
            None => {
                let addr = input.parse::<IpAddr>().ok()?;
                let full = if addr.is_ipv4() { 32 } else { 128 };
                (addr, full)
            }
        };
        let max = if addr.is_ipv4() { 32 } else { 128 };
        (prefix_len <= max).then_some(Self { addr, prefix_len })
    }

    pub fn contains(&self, ip: IpAddr) -> bool {
        match (self.addr, ip) {
            (IpAddr::V4(net), IpAddr::V4(ip)) => {
                if self.prefix_len == 0 {
                    return true;
                }
                let mask = u32::MAX << (32 - u32::from(self.prefix_len));
                u32::from(net) & mask == u32::from(ip) & mask
            }
            (IpAddr::V6(net), IpAddr::V6(ip)) => {
                if self.prefix_len == 0 {
                    return true;
                }
                let mask = u128::MAX << (128 - u32::from(self.prefix_len));
                u128::from(net) & mask == u128::from(ip) & mask
            }
            _ => false,
        }
    }
}

/// 还原客户端 IP：
/// - peer 不在 trusted 列表 → 直接用 peer（转发头不可信）；
/// - peer 可信 → 从 `X-Forwarded-For` 右往左取第一个**不可信**地址
///   （右侧条目是最近一跳可信代理追加的，左侧可被客户端伪造）；
/// - XFF 缺失或全部可信 → 退回 `X-Real-IP`，再退回 peer。
pub fn resolve_client_ip(peer: IpAddr, headers: &HeaderMap, trusted: &[IpNet]) -> IpAddr {
    let is_trusted = |ip: IpAddr| trusted.iter().any(|net| net.contains(ip));
    if trusted.is_empty() || !is_trusted(peer) {
        return peer;
    }
    if let Some(xff) = headers
        .get("x-forwarded-for")
        .and_then(|value| value.to_str().ok())
    {
        for candidate in xff.rsplit(',') {
            match candidate.trim().parse::<IpAddr>() {
                Ok(ip) if !is_trusted(ip) => return ip,
                Ok(_) => continue,
                // 无法解析的片段视为伪造，不再向左信任
                Err(_) => break,
            }
        }
    }
    if let Some(real_ip) = headers
        .get("x-real-ip")
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.trim().parse::<IpAddr>().ok())
    {
        return real_ip;
    }
    peer
}

#[cfg(test)]
mod tests {
    use super::{resolve_client_ip, IpNet};
    use axum::http::HeaderMap;
    use std::net::IpAddr;

    fn ip(s: &str) -> IpAddr {
        s.parse().unwrap()
    }

    fn headers(pairs: &[(&'static str, &str)]) -> HeaderMap {
        let mut map = HeaderMap::new();
        for (key, value) in pairs {
            map.insert(*key, value.parse().unwrap());
        }
        map
    }

    #[test]
    fn parses_cidr_and_bare_ip() {
        assert!(IpNet::parse("10.0.0.0/8").unwrap().contains(ip("10.1.2.3")));
        assert!(!IpNet::parse("10.0.0.0/8").unwrap().contains(ip("11.0.0.1")));
        assert!(IpNet::parse("192.168.1.5").unwrap().contains(ip("192.168.1.5")));
        assert!(!IpNet::parse("192.168.1.5").unwrap().contains(ip("192.168.1.6")));
        assert!(IpNet::parse("::1/128").unwrap().contains(ip("::1")));
        assert!(IpNet::parse("0.0.0.0/0").unwrap().contains(ip("8.8.8.8")));
        assert!(IpNet::parse("10.0.0.0/33").is_none());
        assert!(IpNet::parse("not-an-ip").is_none());
        // v4 网段不匹配 v6 地址
        assert!(!IpNet::parse("10.0.0.0/8").unwrap().contains(ip("::1")));
    }

    #[test]
    fn untrusted_peer_ignores_forwarded_headers() {
        let trusted = vec![IpNet::parse("172.16.0.0/12").unwrap()];
        let headers = headers(&[("x-forwarded-for", "1.2.3.4")]);

        assert_eq!(
            resolve_client_ip(ip("8.8.8.8"), &headers, &trusted),
            ip("8.8.8.8")
        );
        // trusted 为空 → 永远用 peer
        assert_eq!(resolve_client_ip(ip("172.18.0.2"), &headers, &[]), ip("172.18.0.2"));
    }

    #[test]
    fn trusted_peer_takes_rightmost_untrusted_xff_entry() {
        let trusted = vec![IpNet::parse("172.16.0.0/12").unwrap()];

        // 常规：nginx 追加真实客户端 IP
        let h = headers(&[("x-forwarded-for", "203.0.113.7")]);
        assert_eq!(resolve_client_ip(ip("172.18.0.2"), &h, &trusted), ip("203.0.113.7"));

        // 客户端伪造左侧条目，取最右不可信的仍是真实 IP
        let h = headers(&[("x-forwarded-for", "6.6.6.6, 203.0.113.7")]);
        assert_eq!(resolve_client_ip(ip("172.18.0.2"), &h, &trusted), ip("203.0.113.7"));

        // 多级可信代理：跳过右侧可信条目
        let h = headers(&[("x-forwarded-for", "203.0.113.7, 172.20.0.9")]);
        assert_eq!(resolve_client_ip(ip("172.18.0.2"), &h, &trusted), ip("203.0.113.7"));
    }

    #[test]
    fn falls_back_to_real_ip_then_peer() {
        let trusted = vec![IpNet::parse("172.16.0.0/12").unwrap()];

        // XFF 缺失 → X-Real-IP
        let h = headers(&[("x-real-ip", "203.0.113.9")]);
        assert_eq!(resolve_client_ip(ip("172.18.0.2"), &h, &trusted), ip("203.0.113.9"));

        // 两个头都没有 → peer
        let h = HeaderMap::new();
        assert_eq!(resolve_client_ip(ip("172.18.0.2"), &h, &trusted), ip("172.18.0.2"));

        // XFF 全部可信（LAN 客户端也在 trusted 段内）→ X-Real-IP 兜底
        let h = headers(&[
            ("x-forwarded-for", "172.19.0.8"),
            ("x-real-ip", "172.19.0.8"),
        ]);
        assert_eq!(resolve_client_ip(ip("172.18.0.2"), &h, &trusted), ip("172.19.0.8"));
    }
}
