package co.com.ticketing.dynamodb;

import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.List;

/**
 * Reads why DynamoDB cancelled a transaction. The reasons come in the same order as the items of the transaction.
 */
final class TransactionCancellation {

    private static final String CONDITIONAL_CHECK_FAILED = "ConditionalCheckFailed";
    private static final String TRANSACTION_CONFLICT = "TransactionConflict";

    private TransactionCancellation() {
    }

    static boolean failedCondition(TransactionCanceledException error, int item) {
        List<String> codes = codes(error);
        return codes.size() > item && CONDITIONAL_CHECK_FAILED.equals(codes.get(item));
    }

    static boolean conflicted(TransactionCanceledException error) {
        return codes(error).contains(TRANSACTION_CONFLICT);
    }

    private static List<String> codes(TransactionCanceledException error) {
        return error.cancellationReasons().stream().map(CancellationReason::code).toList();
    }
}
