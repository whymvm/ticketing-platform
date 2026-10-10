package co.com.ticketing.api;

import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;


@Configuration
public class RouterRest {

    @RouterOperations({
            @RouterOperation(
                    path = ApiPaths.EVENTS,
                    beanClass = EventHandler.class,
                    beanMethod = "createEvent",
                    method = RequestMethod.POST
            )
    })
    @Bean
    public RouterFunction<ServerResponse> routerFunction(EventHandler eventHandler, ErrorHandler errorHandler) {
        return RouterFunctions
                .route()
                .POST(ApiPaths.EVENTS, eventHandler::createEvent)
                .onError(Throwable.class, errorHandler::handle)
                .build();
    }
}
