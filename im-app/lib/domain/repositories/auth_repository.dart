import '../entities/user.dart';

abstract interface class AuthRepository {
  Stream<User?> watchCurrentUser();
  Future<User> login({required String account, required String password});
  Future<void> logout();
}
