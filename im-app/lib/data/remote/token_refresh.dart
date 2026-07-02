/// token 刷新结果三态——重连状态机据此决定「登出」还是「稍后重试」。
///
/// im-web 的问题：AUTH 失败后刷新只用 bool，网络抖动导致的刷新失败被当成「凭证失效」
/// 直接登出，体验差且会与双端互踢叠加成抖动。这里显式区分三态：
/// - success      刷新成功，用新 token 重连
/// - authInvalid  refresh token 也已失效（401/403）或账号封禁 → 真正登出
/// - networkError 网络/服务端临时错误 → 不登出，退避后重连重试
enum TokenRefreshResult { success, authInvalid, networkError }
