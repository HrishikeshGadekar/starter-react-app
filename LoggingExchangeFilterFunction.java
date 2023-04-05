WebClient webClient = WebClient.builder()
        .baseUrl("http://example.com")
        .filter(ExchangeFilterFunctions.ofRequestProcessor(request -> {
            if (log.isTraceEnabled()) {
                log.trace("Request: {} {} {} {}",
                        request.method(),
                        request.url(),
                        request.headers(),
                        request.bodyToMono(String.class));
            } else {
                log.debug("Request: {} {}",
                        request.method(),
                        request.url());
            }

            return request;
        }))
        .filter((clientRequest, next) -> {
            long startTime = System.currentTimeMillis();

            return next.exchange(clientRequest)
                    .doOnSuccess(clientResponse -> {
                        long duration = System.currentTimeMillis() - startTime;

                        if (log.isTraceEnabled()) {
                            log.trace("Response: {} {} {} {} ({} ms)",
                                    clientResponse.statusCode(),
                                    clientResponse.statusCode().getReasonPhrase(),
                                    clientResponse.headers().asHttpHeaders(),
                                    clientResponse.bodyToMono(String.class),
                                    duration);
                        } else {
                            log.debug("Response: {} {} ({} ms)",
                                    clientResponse.statusCode(),
                                    clientResponse.statusCode().getReasonPhrase(),
                                    duration);
                        }
                    })
                    .doOnError(throwable -> {
                        long duration = System.currentTimeMillis() - startTime;

                        log.error("Error making request: {} ({} ms)", throwable.getMessage(), duration);
                    });
        })
        .build();


// No check for Trace or Debug level

WebClient webClient = WebClient.builder()
        .baseUrl("http://example.com")
        .filter(ExchangeFilterFunctions.ofRequestProcessor(request -> {
            log.trace("Request: {} {} {} {}",
                    request.method(),
                    request.url(),
                    request.headers(),
                    request.bodyToMono(String.class));

            return request;
        }))
        .filter((clientRequest, next) -> {
            long startTime = System.currentTimeMillis();

            return next.exchange(clientRequest)
                    .doOnSuccess(clientResponse -> {
                        long duration = System.currentTimeMillis() - startTime;

                        log.trace("Response: {} {} {} {} ({} ms)",
                                clientResponse.statusCode(),
                                clientResponse.statusCode().getReasonPhrase(),
                                clientResponse.headers().asHttpHeaders(),
                                clientResponse.bodyToMono(String.class),
                                duration);
                    })
                    .doOnError(throwable -> {
                        long duration = System.currentTimeMillis() - startTime;

                        log.error("Error making request: {} ({} ms)", throwable.getMessage(), duration);
                    });
        })
        .build();
