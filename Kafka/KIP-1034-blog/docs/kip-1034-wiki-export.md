Source URL: https://cwiki.apache.org/confluence/display/KAFKA/KIP-1034%3A+Dead+letter+queue+in+Kafka+Streams
Title: KIP-1034: Dead letter queue in Kafka Streams - Apache Kafka - Apache Software Foundation

DUE TO SPAM, SIGN-UP IS DISABLED. Goto Selfserve wiki signup and request an account.

Log in 

Linked Applications

Loading…

Apache Software Foundation
* Spaces

* Hit enter to search
* Help  
   * Online Help  
   * Keyboard Shortcuts  
   * Feed Builder  
   * What’s new  
   * What’s new  
   * Available Gadgets  
   * About Confluence
* Log in

  
Apache Kafka

Apache Kafka

* Pages
* Blog

## Space shortcuts

* Retrospectives

##### Child pages

* Kafka Improvement Proposals

* KIP-1034: Dead letter queue in Kafka Streams

Browse pages

ConfigureSpace tools

* * Attachments (0)  
   * Page History  
   * Resolved comments  
   * Page Information  
   * View in Hierarchy  
   * View Source  
   * Export to PDF  
   * Export to Word  
   * Copy Page Tree

1. Pages
2. Index
3. Kafka Improvement Proposals

* <>
* Jira links

# KIP-1034: Dead letter queue in Kafka Streams 

* Created by Damien Gasparina, last modified by Chia-Ping Tsai on Feb 24, 2026

# Status

**Current state**: A_dopted_

**Discussion thread**: _here_ 

**JIRA**: _here_ 

Please keep the discussion on the mailing list rather than commenting on the wiki (wiki discussions get unwieldy fast).

# Motivation

_KIP inspired by Michelin kstreamplify and coauthored by Damien Gasparina, Loic Greffier and Sebastien Viale._

Kafka Streams does have multiple exception handlers to handle issues while processing messages. Each handler proposes two options: either to log the faulty message and continue processing, or to fail and stop KafkaStreams, the default value.

Both out-of-the-box implementations are not suitable for most use-cases as stopping Kafka Streams due to a single faulty message might be problematic and logging and skipping is at high risk of being missed if the user does not actually check the logs.

Most applications tend to rely on the Dead Letter Queue (DLQ) pattern: in case of an issue, the faulty message that can not be processed is stored in a separate topic. This approach has many advantages:

1. It is easy to access or replay faulty messages.
2. It is easy to configure an automated notification if messages are produced in the DLQ topic.
3. Many metadata could be added in the DLQ record, e.g. stacktrace, source topic/partition/offset, etc…
4. The DLQ topic could have a different retention than the application log.

DLQ pattern is becoming a standard, it is already available out of the box in Kafka Connect. Many applications I worked with already implemented this pattern in Kafka Streams. Including a DLQ feature directly in Kafka Streams would allow users to configure production-ready error handlers without having to write custom code.

# Proposed Changes

To allow users to send a record in the Dead letter queue, a new attribute "deadLetterQueueRecord'' will be added in each exception handler's responses. If this attribute is set, KafkaStreams will send the provided record to Kafka.

A new configuration will be added: errors.dead.letter.queue.topic.name. When set, this configuration indicates the default exception handler implementation to build a Dead letter queue record during the error handling.

In order to build a valid Dead letter queue payload, some additional information needs to be captured and forwarded in the processor context: the source message raw key and value.

Storing the raw key and the raw value allows us to send those raw information in the DLQ topic without having to infer the right serializer. All metadata, e.g. Exceptions, StackTrace, topic, partitions and offset would be provided in the record headers by default.

Additionally, the ProcessingContext would need to be available in each ExceptionHandler. It is currently not available in the ProductionExceptionHandler, thus the handle method will need to be overloaded to provide the context and a default implementation needs to be provided to ensure backward compatibility.

If the default values are not suitable for an application, developers could still reimplement the required exception handlers to build custom DLQ records.

This proposal is to:

1. Add a new getter and setter to configure dead letter queue records in the DeserializationExceptionHandler, ProductionExceptionHandler and ProcessExceptionHandler.
2. Add a new attribute public static final String ERRORS\_DEAD\_LETTER\_QUEUE\_TOPIC\_NAME\_CONFIG = "errors.dead.letter.queue.topic.name".
3. Change the existing exception handler to produce a DeadLetterQueue record if the parameter errors.dead.letter.queue.topic.name is set.
4. If the DeadLetterQueue record can not be sent to Apache Kafka, the exception would be sent to the Kafka Streams uncaughtExceptionHandler.

  
## Default Dead letter queue record

| Key                                   | Key of the input message that triggered the sub-topology, null if triggered by punctuate                              |
| ------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| Value                                 | If available, contains the value of the input message that triggered the sub-topology, null if triggered by punctuate |
| Headers                               | Existing context headers are automatically forwarded into the new DLQ record                                          |
| Header: \_\_streams.errors.exception  | Name of the thrown exception                                                                                          |
| Header: \_\_streams.errors.stacktrace | Stacktrace of the thrown exception                                                                                    |
| Header: \_\_streams.errors.message    | Thrown exception message                                                                                              |
| Header: \_\_streams.errors.topic      | Source input topic, null if triggered by punctuate                                                                    |
| Header: \_\_streams.errors.partition  | Source input partition, null if triggered by punctuate                                                                |
| Header: \_\_streams.errors.offset     | Source input offset, null if triggered by punctuate                                                                   |

## Default Dead letter queue topic

By default, this KIP proposes to have on DLQ topic per Kafka Streams application. This topic would not be automatically created by Kafka Streams.

The DLQ topic name is set through the configuration ERRORS\_DEAD\_LETTER\_QUEUE\_TOPIC\_NAME\_CONFIG \= " errors.dead.letter.queue.topic.name". Users can override the default behavior by implementing custom exception handlers to implement a different DLQ topic strategy if required.

# Public Interfaces

## StreamsConfig.java

Changes:

* Adding the errors.dead.letter.queue.topic.name configuration. This configuration is only modifying the behavior of the out of the box exceptions handlers and would have no effect if a custom exception handlers is implemented, for example:  
   * if errors.dead.letter.queue.topic.name=null (default), then no records are sent to any dead letter queue  
   * if errors.dead.letter.queue.topic.name is set, exceptions happening during processing, production or deserialization will result in the raw source messages that trigger the topology to be send to the DLQ topic. The processing might or might not continue depending of the configuration of the processing.exception.handler, default.production.exception.handler and default.deserialization.exception.handler configurations

public static final String ERRORS_DEAD_LETTER_QUEUE_TOPIC_NAME_CONFIG = "errors.dead.letter.queue.topic.name";

.define(ERRORS_DEAD_LETTER_QUEUE_TOPIC_NAME_CONFIG,
       Type.STRING,
       null, /* default */
       Importance.MEDIUM,
       ERRORS_DEAD_LETTER_QUEUE_TOPIC_NAME_DOC)

  
If the user implements a custom exception handler, it is up to the custom handler to build DLQ records to send, in this case, the errors.dead.letter.queue.topic.name configuration has no impact.  

    @Override
    public Response handleError(final ErrorHandlerContext context, final Record<?, ?> record, final Exception exception) {         
        List<ProducerRecord<byte[], byte[]>> records = Collections.singletonList(new ProducerRecord<>("app-dlq", "Hello".getBytes(StandardCharsets.UTF_8), "World".getBytes(StandardCharsets.UTF_8)));

        return ProcessingExceptionHandler.Response.resume(records);  
   }

## ProductionExceptionHandler.java

Changes:

* Adding a Response nested class,that contains a Result indicating whether to continue processing, fail, or retry and the list of records to be sent to the dead letter queue topic
* Deprecating the handler() and handlerSerializationException() methods and adding two new methods: handlerError() and handleSerializationException(). The return type is the Response
* Deprecating the ProductionExceptionHandlerResponse enum and create a new Result enum. The result enum contains the 3 fields RESUME, FAIL and RETRY and a deprecated method to convert the old enum to the new one
* As the ErrorHandlerContext does not provide the sourceKey/Value in the handle method to limit the memory impact, the Dead Letter Queue record would only contains metadata. handleSerializationException is not impacted.

public interface ProductionExceptionHandler extends Configurable {
   ...   
     

    @Deprecated
    default ProductionExceptionHandlerResponse handle(final ErrorHandlerContext context,
                                                      final ProducerRecord<byte[], byte[]> record,
                                                      final Exception exception) {
        ...
    }

    /**
     * Inspect a record that we attempted to produce, and the exception that resulted
     * from attempting to produce it and determine to continue or stop processing.
     *
     * @param context
     *     The error handler context metadata.
     * @param record
     *     The record that failed to produce.
     * @param exception
     *     The exception that occurred during production.
     *
     * @return a {@link Response} object
     */
    default Response handleError(final ErrorHandlerContext context,
                                                    final ProducerRecord<byte[], byte[]> record,
                                                    final Exception exception) {                          
          return new Response(Result.from(handle(context, record, exception)), Collections.emptyList());
     }

    @Deprecated
    default ProductionExceptionHandlerResponse handleSerializationException(final ProducerRecord record,
                                                                            final Exception exception) {
        ...
    }

    /**
     * Handles serialization exception and determine if the process should continue. The default implementation is to
     * fail the process.
     *
     * @param context
     *     The error handler context metadata.
     * @param record
     *     The record that failed to serialize.
     * @param exception
     *     The exception that occurred during serialization.
     * @param origin
     *     The origin of the serialization exception.
     *
     * @return a {@link Response} object
     */     
    default Response handleSerializationError(final ErrorHandlerContext context,
                                                                 final ProducerRecord record,
                                                                 final Exception exception,
                                                                 final SerializationExceptionOrigin origin) {                  
        return new Response(Result.from(handleSerializationException(context, record, exception, origin)), Collections.emptyList());
    }          
    
    @Deprecated
    enum ProductionExceptionHandlerResponse {
      ...
    }

        /**
     * Enumeration that describes the response from the exception handler.
     */
    enum Result {
        /** Resume processing.
         *
         * <p> For this case, output records which could not be written successfully are lost.
         * Use this option only if you can tolerate data loss.
         */
        RESUME(0, "RESUME"),
        /** Fail processing.
         *
         * <p> Kafka Streams will raise an exception and the {@code StreamsThread} will fail.
         * No offsets (for {@link org.apache.kafka.streams.StreamsConfig#AT_LEAST_ONCE at-least-once}) or transactions
         * (for {@link org.apache.kafka.streams.StreamsConfig#EXACTLY_ONCE_V2 exactly-once}) will be committed.
         */
        FAIL(1, "FAIL"),
        /** Retry the failed operation.
         *
         * <p> Retrying might imply that a {@link TaskCorruptedException} exception is thrown, and that the retry
         * is started from the last committed offset.
         *
         * <p> <b>NOTE:</b> {@code RETRY} is only a valid return value for
         * {@link org.apache.kafka.common.errors.RetriableException retriable exceptions}.
         * If {@code RETRY} is returned for a non-retriable exception it will be interpreted as {@link #FAIL}.
         */
        RETRY(2, "RETRY");

        /**
         * An english description for the used option. This is for debugging only and may change.
         */
        public final String name;

        /**
         * The permanent and immutable id for the used option. This can't change ever.
         */
        public final int id;

        Result(final int id, final String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * Converts the deprecated enum ProductionExceptionHandlerResponse into the new Result enum.
         *
         * @param value the old ProductionExceptionHandlerResponse enum value
         * @return a {@link ProductionExceptionHandler.Result} enum value
         * @throws IllegalArgumentException if the provided value does not map to a valid {@link ProductionExceptionHandler.Result}
         */
        private static ProductionExceptionHandler.Result from(final ProductionExceptionHandlerResponse value) {
            switch (value) {
                case FAIL:
                    return Result.FAIL;
                case CONTINUE:
                    return Result.RESUME;
                case RETRY:
                    return Result.RETRY;
                default:
                    throw new IllegalArgumentException("No Result enum found for old value: " + value);
            }
        }
    }

    /**
     * Represents the result of handling a production exception.
     * <p>
     * The {@code Response} class encapsulates a {@link ProductionExceptionHandlerResponse},
     * indicating whether processing should continue or fail, along with an optional list of
     * {@link ProducerRecord} instances to be sent to a dead letter queue.
     * </p>
     */
    class Response {

        private Result result;

        private List<ProducerRecord<byte[], byte[]>> deadLetterQueueRecords;

        /**
         * Constructs a new {@code Response} object.
         *
         * @param result the result indicating whether processing should continue or fail;
         *                                  must not be {@code null}.
         * @param deadLetterQueueRecords    the list of records to be sent to the dead letter queue; may be {@code null}.
         */
        private Response(final Result result,
                         final List<ProducerRecord<byte[], byte[]>> deadLetterQueueRecords) {
            this.result = result;
            this.deadLetterQueueRecords = deadLetterQueueRecords;
        }

        /**
         * Creates a {@code Response} indicating that processing should fail.
         *
         * @param deadLetterQueueRecords the list of records to be sent to the dead letter queue; may be {@code null}.
         * @return a {@code Response} with a {@link ProductionExceptionHandler.Result#FAIL} status.
         */
        public static Response fail(final List<ProducerRecord<byte[], byte[]>> deadLetterQueueRecords) {
            return new Response(Result.FAIL, deadLetterQueueRecords);
        }

        /**
         * Creates a {@code Response} indicating that processing should fail.
         *
         * @return a {@code Response} with a {@link ProductionExceptionHandler.Result#FAIL} status.
         */
        public static Response fail() {
            return fail(Collections.emptyList());
        }

        /**
         * Creates a {@code Response} indicating that processing should continue.
         *
         * @param deadLetterQueueRecords the list of records to be sent to the dead letter queue; may be {@code null}.
         * @return a {@code Response} with a {@link ProductionExceptionHandler.Result#RESUME} status.
         */
        public static Response resume(final List<ProducerRecord<byte[], byte[]>> deadLetterQueueRecords) {
            return new Response(Result.RESUME, deadLetterQueueRecords);
        }

        /**
         * Creates a {@code Response} indicating that processing should continue.
         *
         * @return a {@code Response} with a {@link ProductionExceptionHandler.Result#RESUME} status.
         */
        public static Response resume() {
            return resume(Collections.emptyList());
        }

        /**
         * Creates a {@code Response} indicating that processing should retry.
         *
         * @return a {@code Response} with a {@link ProductionExceptionHandler.Result#RETRY} status.
         */
        public static Response retry() {
            return new Response(Result.RETRY, Collections.emptyList());
        }

        /**
         * Retrieves the production exception handler result.
         *
         * @return the {@link Result} indicating whether processing should continue, fail or retry.
         */
        public Result result() {
            return result;
        }

        /**
         * Retrieves an unmodifiable list of records to be sent to the dead letter queue.
         * <p>
         * If the list is {@code null}, an empty list is returned.
         * </p>
         *
         * @return an unmodifiable list of {@link ProducerRecord} instances
         *         for the dead letter queue, or an empty list if no records are available.
         */
        public List<ProducerRecord<byte[], byte[]>> deadLetterQueueRecords() {
            if (deadLetterQueueRecords == null) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(deadLetterQueueRecords);
        }
    }
}

## DeserializationExceptionHandler.java

Changes:

* Adding a Response nested class,that contains a Result indicating whether to continue processing or fail, and the list of records to be sent to the dead letter queue topic
* Deprecating the handler() method and adding a new method: handlerError(). The return type is the Response
* Deprecating the DeserializationHandlerResponse enum and create a new Result enum. The result enum contains the 2 fields RESUME and Fail and a deprecated method to convert the old enum to the new one

public interface DeserializationExceptionHandler extends Configurable {

   ...      
  
    @Deprecated
    default DeserializationHandlerResponse handle(final ErrorHandlerContext context,
                                                  final ConsumerRecord<byte[], byte[]> record,
                                                  final Exception exception) {
        ...
    }

    /**
     * Inspects a record and the exception received during deserialization.
     *
     * @param context
     *     Error handler context.
     * @param record
     *     Record that failed deserialization.
     * @param exception
     *     The actual exception.
     *
     * @return a {@link Response} object
     */
    default Response handleError(final ErrorHandlerContext context, final ConsumerRecord<byte[], byte[]> record, final Exception exception) {           
        return new Response(Result.from(handle(context, record, exception)), Collections.emptyList());
    }     

    @Deprecated
    enum DeserializationHandlerResponse {
        ...
    }

    /**
     * Enumeration that describes the response from the exception handler.
     */
    enum Result {
        /** Continue processing. */
        RESUME(0, "RESUME"),
        /** Fail processing. */
        FAIL(1, "FAIL");

        /**
         * An english description for the used option. This is for debugging only and may change.
         */
        public final String name;

        /**
         * The permanent and immutable id for the used option. This can't change ever.
         */
        public final int id;

        Result(final int id, final String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * Converts the deprecated enum DeserializationHandlerResponse into the new Result enum.
         *
         * @param value the old DeserializationHandlerResponse enum value
         * @return a {@link Result} enum value
         * @throws IllegalArgumentException if the provided value does not map to a valid {@link Result}
         */
        private static DeserializationExceptionHandler.Result from(final DeserializationHandlerResponse value) {
            switch (value) {
                case FAIL:
                    return Result.FAIL;
                case CONTINUE:
                    return Result.RESUME;
                default:
                    throw new IllegalArgumentException("No Result enum found for old value: " + value);
            }
        }
    }

    /**
     * Represents the result of handling a deserialization exception.
     * <p>
     * The {@code Response} class encapsulates a {@link ProcessingExceptionHandler.Result},
     * indicating whether processing should continue or fail, along with an optional list of
     * {@link ProducerRecord} instances to be sent to a dead letter queue.
     * </p>
     */
    class Response {

        private Result result;

        private List<ProducerRecord<byte[], byte[]>> deadLetterQueueRecords;

        /**
         * Constructs a new {@code DeserializationExceptionResponse} object.
         *
         * @param result the result indicating whether processing should continue or fail;
         *                                  must not be {@code null}.
         * @param deadLetterQueueRecords    the list of records to be sent to the dead letter queue; may be {@code null}.
         */
        private Response(final Result result,
                         final List<ProducerRecord<byte[], byte[]>> deadLetterQueueRecords) {
            this.result = result;
            this.deadLetterQueueRecords = deadLetterQueueRecords;
        }

        /**
         * Creates a {@code Response} indicating that processing should fail.
         *
         * @param deadLetterQueueRecords the list of records to be sent to the dead letter queue; may be {@code null}.
         * @return a {@code Response} with a {@link DeserializationExceptionHandler.Result#FAIL} status.
         */
        public static Response fail(final List<ProducerRecord<byte[], byte[]>> deadLetterQueueRecords) {
            return new Response(Result.FAIL, deadLetterQueueRecords);
        }

        /**
         * Creates a {@code Response} indicating that processing should fail.
         *
         * @return a {@code Response} with a {@link DeserializationExceptionHandler.Result#FAIL} status.
         */
        public static Response fail() {
            return fail(Collections.emptyList());
        }

        /**
         * Creates a {@code Response} indicating that processing should continue.
         *
         * @param deadLetterQueueRecords the list of records to be sent to the dead letter queue; may be {@code null}.
         * @return a {@code Response} with a {@link DeserializationExceptionHandler.Result#RESUME} status.
         */
        public static Response resume(final List<ProducerRecord<byte[], byte[]>> deadLetterQueueRecords) {
            return new Response(Result.RESUME, deadLetterQueueRecords);
        }

        /**
         * Creates a {@code Response} indicating that processing should continue.
         *
         * @return a {@code Response} with a {@link DeserializationHandlerResponse#CONTINUE} status.
         */
        public static Response resume() {
            return resume(Collections.emptyList());
        }

        /**
         * Retrieves the deserialization handler result.
         *
         * @return the {@link Result} indicating whether processing should continue or fail.
         */
        public Result result() {
            return result;
        }

        /**
         * Retrieves an unmodifiable list of records to be sent to the dead letter queue.
         * <p>
         * If the list is {@code null}, an empty list is returned.
         * </p>
         *
         * @return an unmodifiable list of {@link ProducerRecord} instances
         *         for the dead letter queue, or an empty list if no records are available.
         */
        public List<ProducerRecord<byte[], byte[]>> deadLetterQueueRecords() {
            if (deadLetterQueueRecords == null) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(deadLetterQueueRecords);
        }
    }
}

## ProcessingExceptionHandler.java

Changes:

* Adding a Response nested class,that contains a Result indicating whether to continue processing or fail, and the list of records to be sent to the dead letter queue topic
* Deprecating the handler() method and adding a new method: handlerError(). The return type is the Response
* Deprecating the ProcessingHandlerResponse enum and create a new Result enum. The result enum contains the 2 fields RESUME and Fail and a deprecated method to convert the old enum to the new one

public interface ProcessingExceptionHandler extends Configurable {

   ...   
    
    @Deprecated
    default ProcessingHandlerResponse handle(final ErrorHandlerContext context, final Record<?, ?> record, final Exception exception){
       ...
    };

    /**
     * Inspects a record and the exception received during processing.
     *
     * @param context
     *     Processing context metadata.
     * @param record
     *     Record where the exception occurred.
     * @param exception
     *     The actual exception.
     *
     * @return a {@link Response} object
     */
    default Response handleError(final ErrorHandlerContext context, final Record<?, ?> record, final Exception exception) {                          
        return new Response(ProcessingExceptionHandler.Result.from(handle(context, record, exception)), Collections.emptyList());
    }   

    @Deprecated
    enum ProcessingHandlerResponse {
       ...
    } 

    /**
     * Enumeration that describes the response from the exception handler.
     */
    enum Result {
        /** Resume processing. */
        RESUME(1, "RESUME"),
        /** Fail processing. */
        FAIL(2, "FAIL");

        /**
         * An english description for the used option. This is for debugging only and may change.
         */
        public final String name;

        /**
         * The permanent and immutable id for the used option. This can't change ever.
         */
        public final int id;

        Result(final int id, final String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * Converts the deprecated enum ProcessingHandlerResponse into the new Result enum.
         *
         * @param value the old DeserializationHandlerResponse enum value
         * @return a {@link ProcessingExceptionHandler.Result} enum value
         * @throws IllegalArgumentException if the provided value does not map to a valid {@link ProcessingExceptionHandler.Result}
         */
        private static ProcessingExceptionHandler.Result from(final ProcessingHandlerResponse value) {
            switch (value) {
                case FAIL:
                    return Result.FAIL;
                case CONTINUE:
                    return Result.RESUME;
                default:
                    throw new IllegalArgumentException("No Result enum found for old value: " + value);
            }
        }
    }
          /**
     * Represents the result of handling a processing exception.
     * <p>
     * The {@code Response} class encapsulates a {@link Result},
     * indicating whether processing should continue or fail, along with an optional list of
     * {@link org.apache.kafka.clients.producer.ProducerRecord} instances to be sent to a dead letter queue.
     * </p>
     */
    class Response {

        private Result result;

        private List<ProducerRecord<byte[], byte[]>> deadLetterQueueRecords;

        /**
         * Constructs a new {@code ProcessingExceptionResponse} object.
         *
         * @param result the result indicating whether processing should continue or fail;
         *                                  must not be {@code null}.
         * @param deadLetterQueueRecords    the list of records to be sent to the dead letter queue; may be {@code null}.
         */
        private Response(final Result result,
                         final List<ProducerRecord<byte[], byte[]>> deadLetterQueueRecords) {
            this.result = result;
            this.deadLetterQueueRecords = deadLetterQueueRecords;
        }

        /**
         * Creates a {@code Response} indicating that processing should fail.
         *
         * @param deadLetterQueueRecords the list of records to be sent to the dead letter queue; may be {@code null}.
         * @return a {@code Response} with a {@link ProcessingExceptionHandler.Result#FAIL} status.
         */
        public static Response fail(final List<ProducerRecord<byte[], byte[]>> deadLetterQueueRecords) {
            return new Response(Result.FAIL, deadLetterQueueRecords);
        }

        /**
         * Creates a {@code Response} indicating that processing should fail.
         *
         * @return a {@code Response} with a {@link ProcessingExceptionHandler.Result#FAIL} status.
         */
        public static Response fail() {
            return fail(Collections.emptyList());
        }

        /**
         * Creates a {@code Response} indicating that processing should continue.
         *
         * @param deadLetterQueueRecords the list of records to be sent to the dead letter queue; may be {@code null}.
         * @return a {@code Response} with a {@link ProcessingExceptionHandler.Result#RESUME} status.
         */
        public static Response resume(final List<ProducerRecord<byte[], byte[]>> deadLetterQueueRecords) {
            return new Response(Result.RESUME, deadLetterQueueRecords);
        }

        /**
         * Creates a {@code Response} indicating that processing should continue.
         *
         * @return a {@code Response} with a {@link ProcessingExceptionHandler.Result#RESUME} status.
         */
        public static Response resume() {
            return resume(Collections.emptyList());
        }

        /**
         * Retrieves the processing handler result.
         *
         * @return the {@link Result} indicating whether processing should continue or fail.
         */
        public Result result() {
            return result;
        }

        /**
         * Retrieves an unmodifiable list of records to be sent to the dead letter queue.
         * <p>
         * If the list is {@code null}, an empty list is returned.
         * </p>
         *
         * @return an unmodifiable list of {@link ProducerRecord} instances
         *         for the dead letter queue, or an empty list if no records are available.
         */
        public List<ProducerRecord<byte[], byte[]>> deadLetterQueueRecords() {
            if (deadLetterQueueRecords == null) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(deadLetterQueueRecords);
        }
    }
 }

## RecordContext.java

Changes:

* Adding the public byte\[\] sourceRawKey and byte\[\] sourceRawValue in the RecordContext pointing to the source record data

**ErrorHandlerContext.java**

/**
 * RecordContext interface
 */
public interface RecordContext {
    . . .          
    /**
     * Return the non-deserialized byte[] of the input message key if the context has been triggered by a message.
     *
     * <p> If this method is invoked within a {@link Punctuator#punctuate(long)
     * punctuation callback}, or while processing a record that was forwarded by a punctuation
     * callback, it will return {@code null}.
     *
     * <p> If this method is invoked in a sub-topology due to a repartition, the returned key would be one sent
     * to the repartition topic.
     *
     * @return the raw byte of the key of the source message
     */
    byte[] sourceRawKey();


    /**
     * Return the non-deserialized byte[] of the input message value if the context has been triggered by a message.
     *
     * <p> If this method is invoked within a {@link Punctuator#punctuate(long)
     * punctuation callback}, or while processing a record that was forwarded by a punctuation
     * callback, it will return {@code null}.
     *
     * <p> If this method is invoked in a sub-topology due to a repartition, the returned key would be one sent
     * to the repartition topic.
     *
     * @return the raw byte of the value of the source message
     */
    byte[] sourceRawValue();
       . . .
}

## ErrorHandlerContext.java

Changes:

* Adding the public byte\[\] sourceRawKey and byte\[\] sourceRawValue in the ErrorHandlerContext pointing to the source record data

**ErrorHandlerContext.java**

/**
 * ErrorHandlerContext interface
 */
public interface ErrorHandlerContext {
    . . .
     
    /**
    * Return the non-deserialized byte[] of the input message key if the context has been triggered by a message.
     *
     * <p> If this method is invoked within a {@link Punctuator#punctuate(long)
     * punctuation callback}, or while processing a record that was forwarded by a punctuation
     * callback, it will return {@code null}.
     *
     * <p> If this method is invoked in a sub-topology due to a repartition, the returned key would be one sent
     * to the repartition topic.
     *
     * <p> Always returns null if this method is invoked within a
     * {@link ProductionExceptionHandler.handle(ErrorHandlerContext, ProducerRecord, Exception)}
     *
     * @return the raw byte of the key of the source message
     */
     byte[] sourceRawKey();
 
     /**
     * Return the non-deserialized byte[] of the input message value if the context has been triggered by a message.
     *
     * <p> If this method is invoked within a {@link Punctuator#punctuate(long)
     * punctuation callback}, or while processing a record that was forwarded by a punctuation
     * callback, it will return {@code null}.
     *
     * <p> If this method is invoked in a sub-topology due to a repartition, the returned value would be one sent
     * to the repartition topic.
     *
     * <p> Always returns null if this method is invoked within a
     * {@link ProductionExceptionHandler.handle(ErrorHandlerContext, ProducerRecord, Exception)}
     *
     * @return the raw byte of the value of the source message
     */
     byte[] sourceRawValue();
 
    . . .
}

  
# Compatibility, Deprecation, and Migration Plan

All changes are backward compatible and should not impact existing applications.

# Test Plan

* Tests to ensure the backward compatibility of the ProductionExceptionHandler class
* Tests to ensure that default exception handlers are sending record to the DLQ topic if the DLQ topic name is set
* Ensure that failure to send the DLQ record kills the StreamThread
* Ensure that punctuator triggered exceptions are producing the expected payload

# Rejected Alternatives

* Managing DeadLetterQueue directly in the DSL by extending the KStreams interface.
* Providing no default implementation to build the Dead letter queue record and delegating this task to the user.
* Only providing exception and metadata information in the default DLQ implementation.
* Adding a new interface, that could be overloaded by the user, to build the DLQ records.
* Adding dead letter queue records to the enum: problematic because the collection would be shared by all stream threads, leading to unnecessary concurrency and potential transaction issues.

* No labels

Overview

Content Tools

 Powered by a free **Atlassian Confluence Open Source Project License** granted to Apache Software Foundation. Evaluate Confluence today.  

* Powered by Atlassian Confluence 8.5.31
* Printed by Atlassian Confluence 8.5.31
* Report a bug
* Atlassian News

Atlassian

{"serverDuration": 81, "requestCorrelationId": "ed305ee9ec205c18"} 