import os
import struct

import numpy as np

OFFSET = 128
OUTPUT_SIZE = 128
INPUT_SIZE = OFFSET * 2 + OUTPUT_SIZE
SAMPLE_RATE = 96000

RECORD_FORMAT = "<" + INPUT_SIZE * "h" + OUTPUT_SIZE * "B"
SAMPLE_VALUE_MUL_FACTOR = 2**15
MAX_SAMPLE_VALUE = 2**15 - 1
MIN_SAMPLE_VALUE = -2**15
RECORD_SIZE = struct.calcsize( RECORD_FORMAT )

POSITIVE_CLASS_WEIGHT = 2
NEGATIVE_CLASS_WEIGHT = 1 / np.sqrt( POSITIVE_CLASS_WEIGHT )
POSITIVE_CLASS_WEIGHT = np.sqrt( POSITIVE_CLASS_WEIGHT )



def load_marked_signal_file( file_path, start_idx, seq_len ):
    with open( file_path, mode='rb') as file:
        file.seek( start_idx * RECORD_SIZE )
        file_content = file.read( seq_len * RECORD_SIZE )
        assert len( file_content ) % RECORD_SIZE == 0, "File: " + file_path + ", start idx: " + str( start_idx ) + ", len: " + str( len( file_content ) )
        record_cnt = len( file_content ) // RECORD_SIZE

        samples = np.zeros( ( record_cnt, INPUT_SIZE, 1 ), dtype=np.float_ )
        markings = np.zeros( ( record_cnt, OUTPUT_SIZE, 1 ), dtype=np.bool_ )

        for idx in range( 0, record_cnt ):
            record = struct.unpack( RECORD_FORMAT, file_content[ idx * RECORD_SIZE : ( idx + 1 ) * RECORD_SIZE ] )
            inputs = np.asarray( record[ :INPUT_SIZE ], dtype=np.float_ )
            samples[ idx ] = np.expand_dims( inputs / SAMPLE_VALUE_MUL_FACTOR, 1 )
            outputs = np.asarray( record[ INPUT_SIZE: ], dtype=np.bool_ )
            markings[ idx ] = np.expand_dims( outputs, 1 )


    return( samples, markings )


# def save_marked_signal_file( file_path, data ):
#     samples, markings = data
#
#     assert len( samples ) % SAMPLES_PER_RECORD == 0
#     assert len( markings ) == len( samples )
#
#     with open( file_path, mode='wb' ) as file:
#
#         record_cnt = len( samples ) // SAMPLES_PER_RECORD
#
#         for idx in range( 0, record_cnt ):
#             flags = ( markings[ idx * RECORD_SIZE + 0 ] & 0x01 << 7 ) | ( markings[ idx * RECORD_SIZE + 1 ] & 0x01 << 6 ) |     \
#                     ( markings[ idx * RECORD_SIZE + 2 ] & 0x01 << 5 ) | ( markings[ idx * RECORD_SIZE + 3 ] & 0x01 << 4 ) |     \
#                     ( markings[ idx * RECORD_SIZE + 4 ] & 0x01 << 3 ) | ( markings[ idx * RECORD_SIZE + 5 ] & 0x01 << 2 ) |     \
#                     ( markings[ idx * RECORD_SIZE + 6 ] & 0x01 << 1 ) | ( markings[ idx * RECORD_SIZE + 7 ] & 0x01 << 0 )
#             rescaled_samples = samples[ idx * RECORD_SIZE : ( idx + 1 ) * RECORD_SIZE ]
#             rescaled_samples = np.clip( rescaled_samples * SAMPLE_VALUE_MUL_FACTOR, MIN_SAMPLE_VALUE, MAX_SAMPLE_VALUE )
#             rescaled_samples = np.asarray( rescaled_samples, dtype=np.int16 )
#             content = struct.pack( RECORD_FORMAT, np.append( rescaled_samples, flags ) )
#             file.write( content )
#
#         file.flush()


def get_marked_signal_file_length( file_path ):
    return os.path.getsize( file_path ) // RECORD_SIZE


def evaluate_model_generator(model, metrics):
    metrics_learn = model.model.evaluate_generator(generator=model.learning_generator)
    metrics_val = model.model.evaluate_generator(generator=model.validation_generator)
    metrics["learn_size"] = model.learning_generator.get_item_count()
    metrics["validation_size"] = model.validation_generator.get_item_count()

    metrics[model.epochs_measured] = []
    metrics[model.epochs_measured].append(metrics_learn[1:5])
    metrics[model.epochs_measured].append(metrics_val[1:5])

    if model.IS_TEST_DATA_LABELED:
        metrics_test = model.model.evaluate_generator(generator=model.test_generator)
        metrics["test_size"] = model.test_generator.get_item_count()

        metrics[model.epochs_measured].append(metrics_test[1:5])


def write_model_metrics(model, metrics):
    os.makedirs(model.LOG_DIR)
    results_file = open(model.LOG_DIR + "\\results.txt", "w")

    results_file.write("Number of learning samples: " + str(metrics["learn_size"]))
    results_file.write("\nNumber of validation samples: " + str(metrics["validation_size"]))
    if model.IS_TEST_DATA_LABELED:
        results_file.write("\nNumber of testing samples: " + str(metrics["test_size"]))

    results_file.write("\nLearning results:")
    results_file.write("\n  Accuracy, F1 score, Precision, Recall\n")
    for epoch in range(0, model.epochs_measured + 1):
        if epoch in metrics:
            for metric in metrics[epoch][0]:
                results_file.write(str(metric) + ", ")
            results_file.write("\n")
    results_file.write("\n")

    results_file.write("\nValidation results:")
    results_file.write("\n  Accuracy, F1 score, Precision, Recall\n")
    for epoch in range(0, model.epochs_measured + 1):
        if epoch in metrics:
            for metric in metrics[epoch][1]:
                results_file.write(str(metric) + ", ")
            results_file.write("\n")
    results_file.write("\n")

    if model.IS_TEST_DATA_LABELED:
        results_file.write("\nTesting results:")
        results_file.write("\n  Accuracy, F1 score, Precision, Recall\n")
        for epoch in range(0, model.epochs_measured + 1):
            if epoch in metrics:
                for metric in metrics[epoch][2]:
                    results_file.write(str(metric) + ", ")
                results_file.write("\n")
        results_file.write("\n")

        results_file.close()
