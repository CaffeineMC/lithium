package util;

import java.util.function.DoublePredicate;

public class DoubleUtils {

    public static double getFirstTrue(DoublePredicate monotonicRisingPredicate, double lowerBound, double upperBound) {
        if (!Double.isFinite(lowerBound) || !Double.isFinite(upperBound)) {
            throw new IllegalArgumentException("Lower and upper bound must be finite!");
        }
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException("Lower bound must not be greater than upper bound!!");
        }
        if (monotonicRisingPredicate.test(lowerBound)) {
            throw new IllegalArgumentException("Lower bound must not meet predicate!");
        }
        if (!monotonicRisingPredicate.test(upperBound)) {
            throw new IllegalArgumentException("Higher bound must meet predicate!");
        }

        double ret = computeFirstTrue(monotonicRisingPredicate, lowerBound, upperBound);

        if (!monotonicRisingPredicate.test(ret) || monotonicRisingPredicate.test(Math.nextDown(ret))) {
            ret = computeFirstTrue(monotonicRisingPredicate, lowerBound, upperBound); //For debugging
            throw new AssertionError("computeFirstTrue is implemented incorrectly!");
        }
        return ret;
    }

    public static double getLastTrue(DoublePredicate monotonicFallingPredicate, double lowerBound, double upperBound) {
        return Math.nextDown(getFirstTrue(b -> !monotonicFallingPredicate.test(b), lowerBound, upperBound));
    }

    public static double computeFirstTrue(DoublePredicate monotonicRisingPredicate, double lowerBound, double upperBound) {
        //predicate always holds for high, never holds for low

        while (lowerBound < upperBound) {
            if (Math.nextUp(lowerBound) == upperBound) {
                return upperBound;
            }

            double mid = (lowerBound + upperBound) / 2.0;

            if (mid <= lowerBound || mid >= upperBound) {
                //In case of precision issues, use another way of computing mid
                mid = lowerBound + (upperBound - lowerBound) / 2.0;
                if (mid <= lowerBound || mid >= upperBound) {
                    //In case of more precision issues, just use anything between high and low
                    mid = Math.nextUp(lowerBound);
                }
            }

            if (monotonicRisingPredicate.test(mid)) {
                upperBound = mid;
            } else {
                lowerBound = mid;
            }
        }

        return upperBound;
    }
}
