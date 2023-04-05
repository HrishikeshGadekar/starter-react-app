package com.neutrino.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

public class LoggingExchangeFilterFunction extends ExchangeFilterFunction {

    public LoggingExchangeFilterFunction(Logger logger) {
        this.logger = logger;
    }


    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        Context context = Context.of(CLIENT_REQUEST_CONTEXT_KEY, request);

        String requestBody = extractRequestBody(request, context);

        logRequest(request, requestBody.getBytes());

        long startTime = System.currentTimeMillis();
        return next.exchange(request)
                   .flatMap(response -> {
                       long elapsedTime = System.currentTimeMillis() - startTime;

                       byte[] responseBody = logResponse(response, startTime, elapsedTime);

                       return Mono.just(response.bufferBody().map(buffer -> {
                           DataBufferUtils.join(Flux.just(DataBuffer.wrap(responseBody)),
                                                  buffer)
                                          .map(dataBuffer -> {
                                              byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                              dataBuffer.read(bytes);
                                              DataBufferUtils.release(dataBuffer);
                                              return bytes;
                                          })
                                          .subscribe(bodyBytes -> {
                                              logResponse(response, bodyBytes, startTime, elapsedTime);
                                          });
                           return response;
                       }));
                   })
                   .onErrorResume(throwable -> {
                       long elapsedTime = System.currentTimeMillis() - startTime;

                       logErrorResponse(throwable, requestBody.getBytes(), startTime);

                       return Mono.error(throwable);
                   });
    }


    private Mono<? extends Throwable> logErrorResponse(Throwable throwable, byte[] requestBody, long startTime) {
        long elapsedTime = System.currentTimeMillis() - startTime;

        HttpStatus httpStatus;
        if (throwable instanceof WebClientException) {
            httpStatus = ((WebClientException) throwable).getStatusCode();
        } else {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        StringBuilder logMessage = new StringBuilder();

        logMessage.append("[")
                  .append(LocalDateTime.now().toString())
                  .append("] ")
                  .append("Request failed in ")
                  .append(elapsedTime)
                  .append(" ms, status code: ")
                  .append(httpStatus.value())
                  .append(", status text: ")
                  .append(httpStatus.getReasonPhrase())
                  .append(", message: ")
                  .append(throwable.getMessage())
                  .append(System.lineSeparator());

        logError(logMessage.toString(), throwable, requestBody, startTime, elapsedTime);

        return Mono.error(throwable);
    }

    private Mono<String> extractRequestBody(ClientRequest request) {
        if (request.body() == null) {
            return Mono.empty();
        }

        if (request.body() instanceof String) {
            return Mono.just((String) request.body());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.addAll(request.headers());

        Flux<DataBuffer> dataBuffers = request.body(BodyExtractors.toDataBuffers());
        return DataBufferUtils.join(dataBuffers)
                              .map(dataBuffer -> {
                                  byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                  dataBuffer.read(bytes);
                                  DataBufferUtils.release(dataBuffer);
                                  return bytes;
                              })
                              .map(bytes -> new String(bytes, headers.getContentTypeCharset()))
                              .onErrorResume(error -> {
                                  logger.error("Error extracting request body: {}", error.getMessage());
                                  return Mono.empty();
                              });
    }

    private void logRequest(ClientRequest request, byte[] requestBody) {
        if (logger.isDebugEnabled()) {
            logger.debug("Sending request to {}: {}", request.url(), request.method());
            request.headers().forEach((name, values) -> values.forEach(value -> logger.debug("{}={}", name, value)));
            if (requestBody != null) {
                logger.debug("Request body: {}", new String(requestBody, StandardCharsets.UTF_8));
            }
        }
    }

    private byte[] logResponse(ClientResponse response, long startTime, long elapsedTime) {
        HttpStatus statusCode = response.statusCode();

        StringBuilder logMessage = new StringBuilder();

        logMessage.append("[")
                  .append(LocalDateTime.now().toString())
                  .append("] ")
                  .append("Response received in ")
                  .append(elapsedTime)
                  .append(" ms, status code: ")
                  .append(statusCode.value())
                  .append(", status text: ")
                  .append(statusCode.getReasonPhrase())
                  .append(System.lineSeparator());

        HttpHeaders headers = response.headers();
        if (!headers.isEmpty()) {
            logMessage.append("Headers: ")
                      .append(headers)
                      .append(System.lineSeparator());
        }

        byte[] responseBody = response.bodyToMono(DataBuffer.class)
                                      .flatMap(dataBuffer -> {
                                          byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                          dataBuffer.read(bytes);
                                          DataBufferUtils.release(dataBuffer);
                                          return Mono.just(bytes);
                                      })
                                      .block();

        logMessage.append("Response body: ")
                  .append(new String(responseBody))
                  .append(System.lineSeparator());

        logDebug(logMessage.toString());

        return responseBody;
    }


    private void logError(ClientResponse response, String responseBody, Throwable throwable) {
        if (logger.isErrorEnabled()) {
            logger.error("Error while calling {}: {}", response.request().url(), throwable.getMessage());
            response.headers().asHttpHeaders().forEach((name, values) -> values.forEach(value -> logger.error("{}={}", name, value)));
            logger.error("Response body: {}", responseBody, throwable);
        }
    }

    private void logInfo(ClientResponse response, String responseBody) {
        if (logger.isInfoEnabled()) {
            logger.info("Received response from {}: {}", response.request().url(), response.statusCode());
            response.headers().asHttpHeaders().forEach((name, values) -> values.forEach(value -> logger.info("{}={}", name, value)));
            logger.info("Response body: {}", responseBody);
        }
    }

    private void logDebug(ClientResponse response, String responseBody) {
        if (logger.isDebugEnabled()) {
            logger.debug("Received response from {}: {}", response.request().url(), response.statusCode());
            response.headers().asHttpHeaders().forEach((name, values) -> values.forEach(value -> logger.debug("{}={}", name, value)));
            logger.debug("Response body: {}", responseBody);
        }
    }

    private void logWarn(ClientResponse response) {
        if (logger.isWarnEnabled()) {
            logger.warn("Received unexpected response from {}: {}", response.request().url(), response.statusCode());
            response.headers().asHttpHeaders().forEach((name, values) -> values.forEach(value -> logger.warn("{}={}", name, value)));
        }
    }


}
