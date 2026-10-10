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

    private DynamoDBExpressions() {
    }
}
