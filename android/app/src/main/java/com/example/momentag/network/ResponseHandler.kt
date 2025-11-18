package com.example.momentag.network

import com.example.momentag.repository.RemoteRepository
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * ResponseHandler
 *
 * Centralizes error handling for Retrofit responses
 * Converts Retrofit Response to RemoteRepository.Result
 */
object ResponseHandler {
    suspend fun <T> handleResponse(apiCall: suspend () -> Response<T>): RemoteRepository.Result<T> =
        try {
            val response = apiCall()

            if (response.isSuccessful) {
                response.body()?.let { body ->
                    RemoteRepository.Result.Success(body)
                } ?: RemoteRepository.Result.Error(
                    response.code(),
                    "Response body is null",
                )
            } else {
                when (response.code()) {
                    401 -> RemoteRepository.Result.Unauthorized("Authentication failed")
                    400 -> RemoteRepository.Result.BadRequest("Bad request")
                    else ->
                        RemoteRepository.Result.Error(
                            response.code(),
                            "An unknown error occurred: ${response.message()}",
                        )
                }
            }
        } catch (e: HttpException) {
            RemoteRepository.Result.Error(e.code(), e.message())
        } catch (e: IOException) {
            RemoteRepository.Result.Exception(e)
        } catch (e: Exception) {
            RemoteRepository.Result.Exception(e)
        }
}
