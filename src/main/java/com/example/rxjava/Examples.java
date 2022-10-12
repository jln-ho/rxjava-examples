package com.example.rxjava;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Examples {

    public static class ThirdPartyService {

        private static final List<String> database = List.of("foo", "bar", "baz", "bat", "qux");

        public Single<List<String>> getStringsStartingWith(String prefix) {
            return getStringsStartingWith(prefix, false);
        }

        public Single<List<String>> getStringsStartingWith(String prefix, boolean simulateError) {
            if (simulateError) {
                return Single.error(new Exception("Simulated error"));
            }
            return Single.just(database.stream().filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList()));
        }
    }

    /**
     * Issues two concurrent service requests to {@link ThirdPartyService#getStringsStartingWith}.
     * One for primary results, one for fallback results that will be used in case the primary request fails or yields empty results.
     *
     * @param prefix         the prefix to search for
     * @param fallbackPrefix the fallback prefix to search for
     * @param simulateError  true if an error should be simulated when calling {@link ThirdPartyService#getStringsStartingWith} with the prefix, else false
     */
    private static void printStringsStartingWith(String prefix, String fallbackPrefix, boolean simulateError) {
        final var service = new ThirdPartyService();
        System.out.printf("prefix: %s, fallback: %s, simulate error: %s\n", prefix, fallbackPrefix, simulateError);
        final var results = service.getStringsStartingWith(prefix, simulateError);
        final var fallbackResults = service.getStringsStartingWith(fallbackPrefix);
        // TODO: is there a more elegant way of returning fallback results if there is an error or if the primary results are empty?
        final var effectiveResults = results
                .onErrorResumeNext(error -> {
                    System.out.println("error receiving results, using fallback results");
                    return fallbackResults;
                })
                .flatMap(r -> {
                    if (r == null || r.isEmpty()) {
                        System.out.println("results are empty, using fallback results");
                        return fallbackResults;
                    }
                    return Single.just(r);
                });
        System.out.printf("effective results: %s\n", Arrays.toString(effectiveResults.blockingGet().toArray()));
        System.out.println("-----------------------------------------------");
    }

    public static void example1() {
        System.out.println("################## EXAMPLE 1 ##################");

        // Expecting primary results
        printStringsStartingWith("f", "b", false);

        // Expecting fallback results due to error
        printStringsStartingWith("f", "b", true);

        // Expecting fallback results due to empty primary results
        printStringsStartingWith("x", "f", false);
    }

    public static void example2() {
        System.out.println("################## EXAMPLE 2 ##################");
        final var numAttempts = 1000;

        /* Using two conditions representing some internal state of the system
         * we would like to count the number of times when both conditions were true */
        final var condition1 = BehaviorSubject.createDefault(false);
        final var condition2 = BehaviorSubject.createDefault(false);


        // We can do this in a naive, non-threadsafe way...
        final var count = new AtomicInteger(0);
        final var nonThreadSafeDisposable = condition1.observeOn(Schedulers.single()).subscribe(c1 -> {
            if (c1 && condition2.getValue()) { // <-- this condition is not thread safe
                count.incrementAndGet();
            }
        });
        toggleConditions(condition1, condition2, numAttempts);
        // count should be < 1000 because condition is not thread safe
        System.out.printf("non-threadsafe condition reached %d time(s)\n", count.get());
        nonThreadSafeDisposable.dispose();

        // Or we could try to use Observable.combineLatest to make the condition threadsafe
        final var threadSafeCount = new AtomicInteger(0);
        final var threadSafeDisposable = Observable.combineLatest(condition1, condition2, (c1, c2) -> c1 && c2)
                .observeOn(Schedulers.single()).subscribe(bothTrue -> {
                    if (bothTrue) {
                        threadSafeCount.incrementAndGet();
                    }
                });
        toggleConditions(condition1, condition2, numAttempts);
        // threadSafeCount is expected to be equal to numAttempts because Observable.combineLatest() should be threadsafe.
        // But it can go below (albeit very close to) 1000 sometimes!!!
        // TODO why is threadSafeCount not always equal to numAttempts??? What is a _truly_ threadsafe implementation of this example?
        System.out.printf("\"threadsafe\" condition reached %d time(s)\n", threadSafeCount.get());
        threadSafeDisposable.dispose();
    }

    private static void toggleConditions(Subject<Boolean> condition1, Subject<Boolean> condition2, int numAttempts) {
        for (var i = 0; i < numAttempts; i++) {
            condition1.onNext(true);
            condition2.onNext(true);
            condition1.onNext(false);
            condition2.onNext(false);
        }
    }

    public static void main(String[] args) {
        final var example = args[0];
        if ("1".equals(example)) {
            example1();
        } else if ("2".equals(example)) {
            example2();
        }
    }
}
