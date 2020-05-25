import os
import struct

import numpy as np

RECORD_FORMAT = "<hhhhhhhhB"
SAMPLES_PER_RECORD = 8
SAMPLE_VALUE_MUL_FACTOR = 2**15
MAX_SAMPLE_VALUE = 2**15 - 1
MIN_SAMPLE_VALUE = -2**15
RECORD_SIZE = struct.calcsize( RECORD_FORMAT )

def load_marked_signal_file( file_path, start_idx, seq_len ):
    with open( file_path, mode='rb') as file:
        assert start_idx % SAMPLES_PER_RECORD == 0
        assert seq_len % SAMPLES_PER_RECORD == 0
        file.seek( start_idx // SAMPLES_PER_RECORD * RECORD_SIZE )
        file_content = file.read( seq_len // SAMPLES_PER_RECORD * RECORD_SIZE )
        assert len( file_content ) % RECORD_SIZE == 0
        record_cnt = len( file_content ) // RECORD_SIZE

        samples = np.zeros( record_cnt * SAMPLES_PER_RECORD, dtype=np.float_ )
        markings = np.zeros( record_cnt * SAMPLES_PER_RECORD, dtype=np.bool_ )

        for idx in range( 0, record_cnt ):
            s1,s2,s3,s4,s5,s6,s7,s8,flags = struct.unpack( RECORD_FORMAT, file_content[ idx * RECORD_SIZE : ( idx + 1 ) * RECORD_SIZE ] )
            c_samples = np.asarray( [ s1,s2,s3,s4,s5,s6,s7,s8 ], dtype=np.float_ )
            samples[ idx * SAMPLES_PER_RECORD: ( idx + 1 ) * SAMPLES_PER_RECORD ] = c_samples / SAMPLE_VALUE_MUL_FACTOR
            markings[ idx * SAMPLES_PER_RECORD: ( idx + 1 ) * SAMPLES_PER_RECORD ] = \
                [ ( flags >> 7 ) & 0x01, ( flags >> 6 ) & 0x01, ( flags >> 5 ) & 0x01, ( flags >> 4 ) & 0x01,
                  ( flags >> 3 ) & 0x01, ( flags >> 2 ) & 0x01, ( flags >> 1 ) & 0x01, ( flags >> 0 ) & 0x01 ]

    return( samples, markings )


def save_marked_signal_file( file_path, data ):
    samples, markings = data

    assert len( samples ) % SAMPLES_PER_RECORD == 0
    assert len( markings ) == len( samples )

    with open( file_path, mode='wb' ) as file:

        record_cnt = len( samples ) // SAMPLES_PER_RECORD

        for idx in range( 0, record_cnt ):
            flags = ( markings[ idx * RECORD_SIZE + 0 ] & 0x01 << 7 ) | ( markings[ idx * RECORD_SIZE + 1 ] & 0x01 << 6 ) |     \
                    ( markings[ idx * RECORD_SIZE + 2 ] & 0x01 << 5 ) | ( markings[ idx * RECORD_SIZE + 3 ] & 0x01 << 4 ) |     \
                    ( markings[ idx * RECORD_SIZE + 4 ] & 0x01 << 3 ) | ( markings[ idx * RECORD_SIZE + 5 ] & 0x01 << 2 ) |     \
                    ( markings[ idx * RECORD_SIZE + 6 ] & 0x01 << 1 ) | ( markings[ idx * RECORD_SIZE + 7 ] & 0x01 << 0 )
            rescaled_samples = samples[ idx * RECORD_SIZE : ( idx + 1 ) * RECORD_SIZE ]
            rescaled_samples = np.clip( rescaled_samples * SAMPLE_VALUE_MUL_FACTOR, MIN_SAMPLE_VALUE, MAX_SAMPLE_VALUE )
            rescaled_samples = np.asarray( rescaled_samples, dtype=np.int16 )
            content = struct.pack( RECORD_FORMAT, np.append( rescaled_samples, flags ) )
            file.write( content )

        file.flush()


def get_marked_signal_file_length( file_path ):
    return os.path.getsize( file_path ) // RECORD_SIZE * SAMPLES_PER_RECORD


def evaluate_model_generator(model, metrics):
    metrics_learn = model.model.evaluate_generator(generator=model.learning_generator)
    metrics_val = model.model.evaluate_generator(generator=model.validation_generator)
    metrics["learn_size"] = model.learning_generator.get_item_count()
    metrics["validation_size"] = model.validation_generator.get_item_count()

    metrics[model.epochs_measured] = []
    metrics[model.epochs_measured].append(metrics_learn[1:3])
    metrics[model.epochs_measured].append(metrics_val[1:3])

    if model.IS_TEST_DATA_LABELED:
        metrics_test = model.model.evaluate_generator(generator=model.test_generator)
        metrics["test_size"] = model.test_generator.get_item_count()

        metrics[model.epochs_measured].append(metrics_test[1:3])


def write_model_metrics(model, metrics):
    os.makedirs(model.LOG_DIR)
    results_file = open(model.LOG_DIR + "\\results.txt", "w")

    results_file.write("Number of learning samples: " + str(metrics["learn_size"]))
    results_file.write("\nNumber of validation samples: " + str(metrics["validation_size"]))
    if model.IS_TEST_DATA_LABELED:
        results_file.write("\nNumber of testing samples: " + str(metrics["test_size"]))

    results_file.write("\nLearning results:")
    results_file.write("\n  IoU,   Dice\n")
    for epoch in range(0, model.epochs_measured + 1):
        if epoch in metrics:
            for metric in metrics[epoch][0]:
                results_file.write(str(metric) + ", ")
            results_file.write("\n")
    results_file.write("\n")

    results_file.write("\nValidation results:")
    results_file.write("\n  IoU,   Dice\n")
    for epoch in range(0, model.epochs_measured + 1):
        if epoch in metrics:
            for metric in metrics[epoch][1]:
                results_file.write(str(metric) + ", ")
            results_file.write("\n")
    results_file.write("\n")

    if model.IS_TEST_DATA_LABELED:
        results_file.write("\nTesting results:")
        results_file.write("\n  IoU,   Dice\n")
        for epoch in range(0, model.epochs_measured + 1):
            if epoch in metrics:
                for metric in metrics[epoch][2]:
                    results_file.write(str(metric) + ", ")
                results_file.write("\n")
        results_file.write("\n")

        results_file.close()
