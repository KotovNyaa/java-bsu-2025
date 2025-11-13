package com.bank.core.engine;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;

import com.bank.core.engine.consumers.BusinessLogicConsumer;
import com.bank.core.engine.consumers.JournalingConsumer;
import com.bank.core.engine.consumers.ReplicationConsumer;

/**
 * Центральный класс-оркестратор, который настраивает и запускает конвейер обработки LMAX Disruptor.
 */
public class TransactionRingBuffer {

    private final Disruptor<TransactionEvent> disruptor;
    private final RingBuffer<TransactionEvent> ringBuffer;

    public TransactionRingBuffer(
            WaitStrategy waitStrategy,
            JournalingConsumer journalingConsumer,
            ReplicationConsumer replicationConsumer,
            BusinessLogicConsumer businessLogicConsumer) {

        this.disruptor = new Disruptor<>(
                TransactionEvent.EVENT_FACTORY,
                1024 * 16, // Ring Buffer Size
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI, // Assuming multiple threads can publish
                waitStrategy
        );

        // Сначала команда параллельно пишется в журнал и отправляется реплике.
        // Только после успешного завершения ОБЕИХ операций, она попадает в бизнес-логику.
        this.disruptor
                .handleEventsWith(journalingConsumer, replicationConsumer)
                .then(businessLogicConsumer);

        this.ringBuffer = disruptor.getRingBuffer();
    }

    public void start() {
        disruptor.start();
    }

    public void stop() {
        disruptor.shutdown();
    }

    public RingBuffer<TransactionEvent> getRingBuffer() {
        return ringBuffer;
    }
}