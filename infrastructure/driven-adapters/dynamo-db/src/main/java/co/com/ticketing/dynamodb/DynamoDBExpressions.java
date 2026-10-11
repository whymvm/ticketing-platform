package co.com.ticketing.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.Expression;

/**
 * Condition expressions shared by the DynamoDB adapters.
 */
final class DynamoDBExpressions {

    // Makes a put fail instead of overwriting an existing item with the same id
    static final Expression ID_NOT_EXISTS = Expression.builder()
            .expression("attribute_not_exists(#id)")
            .putExpressionName("#id", "id")
            .build();

    // Makes an order update fail if another process already moved the order out of QUEUED
    static final String STATUS_IS_QUEUED = "#status = :queued";
    static final String STATUS_NAME = "#status";
    static final String QUEUED_VALUE = ":queued";

    private DynamoDBExpressions() {
    }
}
