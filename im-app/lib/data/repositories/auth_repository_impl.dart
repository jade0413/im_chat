/// Auth Repository 实现预留文件。
///
/// 当前登录态由 `AuthController` 协调 `AuthApi + TokenStore + ImEngine`。
/// 后续如果把鉴权逻辑完全下沉到 data 层，应在这里实现
/// `domain/repositories/auth_repository.dart`。
class AuthRepositoryImpl {
  const AuthRepositoryImpl();
}
