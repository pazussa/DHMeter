package com.dhmeter.core.result

/**
 * A sealed class representing the result of an operation.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable) : Result<Nothing>()
    data object Loading : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    /**
     * Returns the data if successful, otherwise null.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    /**
     * Returns the data if successful, otherwise the default value.
     */
    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        else -> default
    }

    /**
     * Returns the exception if error, otherwise null.
     */
    fun exceptionOrNull(): Throwable? = when (this) {
        is Error -> exception
        else -> null
    }

    /**
     * Maps the success data to another type.
     */
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> Error(exception)
        Loading -> Loading
    }

    /**
     * Flat maps the success data.
     */
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Error -> Error(exception)
        Loading -> Loading
    }

    /**
     * Executes the block if successful.
     */
    inline fun onSuccess(block: (T) -> Unit): Result<T> {
        if (this is Success) block(data)
        return this
    }

    /**
     * Executes the block if error.
     */
    inline fun onError(block: (Throwable) -> Unit): Result<T> {
        if (this is Error) block(exception)
        return this
    }

    companion object {
        /**
         * Creates a Result from a nullable value.
         */
        fun <T> fromNullable(value: T?, error: () -> Throwable = { NullPointerException() }): Result<T> {
            return if (value != null) Success(value) else Error(error())
        }

        /**
         * Wraps a block execution in a Result.
         */
        inline fun <T> runCatching(block: () -> T): Result<T> {
            return try {
                Success(block())
            } catch (e: Throwable) {
                Error(e)
            }
        }
    }
}
