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
            ),
            @RouterOperation(
                    path = ApiPaths.EVENT_BY_ID,
                    beanClass = EventHandler.class,
                    beanMethod = "retrieveEvent",
                    method = RequestMethod.GET
            ),
            @RouterOperation(
                    path = ApiPaths.ORDERS,
                    beanClass = OrderHandler.class,
                    beanMethod = "placeOrder",
                    method = RequestMethod.POST
            ),
            @RouterOperation(
                    path = ApiPaths.ORDER_BY_ID,
                    beanClass = OrderHandler.class,
                    beanMethod = "retrieveOrder",
                    method = RequestMethod.GET
            ),
            @RouterOperation(
                    path = ApiPaths.ORDER_CONFIRMATION,
                    beanClass = OrderHandler.class,
                    beanMethod = "confirmOrder",
                    method = RequestMethod.POST
            )
    })
    @Bean
    public RouterFunction<ServerResponse> routerFunction(EventHandler eventHandler, OrderHandler orderHandler,
                                                         ErrorHandler errorHandler) {
        return RouterFunctions
                .route()
                .POST(ApiPaths.EVENTS, eventHandler::createEvent)
                .GET(ApiPaths.EVENT_BY_ID, eventHandler::retrieveEvent)
                .POST(ApiPaths.ORDERS, orderHandler::placeOrder)
                .GET(ApiPaths.ORDER_BY_ID, orderHandler::retrieveOrder)
                .POST(ApiPaths.ORDER_CONFIRMATION, orderHandler::confirmOrder)
                .onError(Throwable.class, errorHandler::handle)
                .build();
    }
}
