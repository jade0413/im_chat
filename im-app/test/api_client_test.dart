import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:im_app/data/remote/rest/api_client.dart';

void main() {
  test('describeApiError reads backend ApiResponse message', () {
    final requestOptions = RequestOptions(path: '/api/v1/files/presign');
    final error = DioException(
      requestOptions: requestOptions,
      response: Response<Map<String, dynamic>>(
        requestOptions: requestOptions,
        statusCode: 400,
        data: const {
          'code': 3003,
          'message': 'mime not allowed',
          'data': null,
        },
      ),
      type: DioExceptionType.badResponse,
    );

    expect(describeApiError(error), 'mime not allowed');
  });

  test('describeApiError reads object storage XML message', () {
    final requestOptions = RequestOptions(path: 'http://localhost:9000/object');
    final error = DioException(
      requestOptions: requestOptions,
      response: Response<String>(
        requestOptions: requestOptions,
        statusCode: 400,
        data: '<Error><Message>signature mismatch</Message></Error>',
      ),
      type: DioExceptionType.badResponse,
    );

    expect(describeApiError(error), 'signature mismatch');
  });
}
