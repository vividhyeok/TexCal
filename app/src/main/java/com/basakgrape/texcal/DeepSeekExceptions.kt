package com.basakgrape.texcal

import java.io.IOException

open class DeepSeekClientException(message: String, cause: Throwable? = null) : IOException(message, cause)

class DeepSeekAuthException(message: String) : DeepSeekClientException(message)

class DeepSeekRateLimitException(message: String) : DeepSeekClientException(message)

class DeepSeekServerException(message: String) : DeepSeekClientException(message)

class DeepSeekNetworkException(message: String, cause: Throwable? = null) :
    DeepSeekClientException(message, cause)

class DeepSeekResponseFormatException(message: String) : DeepSeekClientException(message)

class DeepSeekApiException(message: String) : DeepSeekClientException(message)
