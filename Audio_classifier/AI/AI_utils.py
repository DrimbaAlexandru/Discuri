import os
import struct

import numpy as np
from sklearn.metrics import classification_report

OFFSET = 128
OUTPUT_SIZE = 1
INPUT_SIZE = OFFSET * 2 + OUTPUT_SIZE
SAMPLE_RATE = 96000

RECORD_FORMAT = "<" + INPUT_SIZE * "h" + OUTPUT_SIZE * "B"
SAMPLE_VALUE_MUL_FACTOR = 2**15
MAX_SAMPLE_VALUE = 2**15 - 1
MIN_SAMPLE_VALUE = -2**15
RECORD_SIZE = struct.calcsize( RECORD_FORMAT )

POSITIVE_CLASS_WEIGHT = 3
NEGATIVE_CLASS_WEIGHT = 1 / np.sqrt( POSITIVE_CLASS_WEIGHT )
POSITIVE_CLASS_WEIGHT = np.sqrt( POSITIVE_CLASS_WEIGHT )



def load_marked_signal_file( file_path, start_idx, seq_len ):
    with open( file_path, mode='rb') as file:
        file.seek( start_idx * RECORD_SIZE )
        file_content = file.read( seq_len * RECORD_SIZE )
        assert len( file_content ) % RECORD_SIZE == 0, "File: " + file_path + ", start idx: " + str( start_idx ) + ", len: " + str( len( file_content ) )
        record_cnt = len( file_content ) // RECORD_SIZE

        samples = np.zeros( ( record_cnt, INPUT_SIZE ), dtype=np.float_ )
        markings = np.zeros( ( record_cnt, OUTPUT_SIZE ), dtype=np.int32 )

        for idx in range( 0, record_cnt ):
            record = struct.unpack( RECORD_FORMAT, file_content[ idx * RECORD_SIZE : ( idx + 1 ) * RECORD_SIZE ] )
            inputs = np.asarray( record[ :INPUT_SIZE ], dtype=np.float_ )
            samples[ idx ] = inputs / SAMPLE_VALUE_MUL_FACTOR
            outputs = np.asarray( record[ INPUT_SIZE: ], dtype=np.int32 )
            markings[ idx ] = outputs


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
    # metrics_learn = model.model.evaluate_generator(generator=model.learning_generator)
    # metrics_val = model.model.evaluate_generator(generator=model.validation_generator)
    # metrics["learn_size"] = model.learning_generator.get_item_count()
    # metrics["validation_size"] = model.validation_generator.get_item_count()
    #
    # metrics[model.epochs_measured] = []
    # metrics[model.epochs_measured].append(metrics_learn[1:5])
    # metrics[model.epochs_measured].append(metrics_val[1:5])
    #
    # if model.IS_TEST_DATA_LABELED:
    #     metrics_test = model.model.evaluate_generator(generator=model.test_generator)
    #     metrics["test_size"] = model.test_generator.get_item_count()
    #
    #     metrics[model.epochs_measured].append(metrics_test[1:5])

    from AI.AI_1D_classifier import iou_coef
    from keras import backend as K

    metrics["learn_size"] = model.learning_generator.get_item_count()
    metrics["validation_size"] = model.validation_generator.get_item_count()
    metrics[ model.epochs_measured ] = {}

    model.learning_generator.shuffle = False
    model.validation_generator.shuffle = False
    model.test_generator.shuffle = False

    # y_pred_learn = model.model.predict_generator( generator = model.learning_generator, verbose = 1 )
    # y_pred_learn = np.where( y_pred_learn < 0.5, 0, 1 )
    # y_true_learn = model.learning_generator.get_all_y_true()

    y_pred_val = model.model.predict_generator( generator = model.validation_generator, verbose = 1 )
    y_pred_val = np.where( y_pred_val < 0.5, 0, 1 )
    y_true_val = model.validation_generator.get_all_y_true()

    # metrics[ model.epochs_measured ]["learn"] = classification_report( np.reshape( y_true_learn, (-1,) ), np.reshape( y_pred_learn, (-1,) ) )
    metrics[ model.epochs_measured ]["validation"] = {}
    metrics[ model.epochs_measured ]["validation"]["confusion matrix"] = classification_report( np.reshape( y_true_val, (-1,) ), np.reshape( y_pred_val, (-1,) ) )
    # metrics[ model.epochs_measured ]["validation"]["IoU"] = K.eval( iou_coef( y_true_val, y_pred_val ) )

    if model.IS_TEST_DATA_LABELED:
        metrics["test_size"] = model.test_generator.get_item_count()

        y_pred_test = model.model.predict_generator( generator = model.test_generator, verbose = 1 )
        y_pred_test = np.where( y_pred_test < 0.5, 0, 1 )
        y_true_test = model.test_generator.get_all_y_true()

        metrics[ model.epochs_measured ]["test"] = {}
        metrics[ model.epochs_measured ]["test"]["confusion matrix"] = classification_report( np.reshape( y_true_test, (-1,) ), np.reshape( y_pred_test, (-1,) ) )
        # metrics[ model.epochs_measured ]["test"]["IoU"] = K.eval( iou_coef( y_true_val, y_pred_val ) )

    model.learning_generator.shuffle = True
    model.validation_generator.shuffle = True
    model.test_generator.shuffle = True


def write_model_metrics(model, metrics):
    os.makedirs(model.LOG_DIR, exist_ok = True )
    results_file = open(model.LOG_DIR + "\\results.txt", "w")

    results_file.write(model.MODEL_PATH)
    results_file.write("\nNumber of learning samples: " + str(metrics["learn_size"]))
    results_file.write("\nNumber of validation samples: " + str(metrics["validation_size"]))
    if model.IS_TEST_DATA_LABELED:
        results_file.write("\nNumber of testing samples: " + str(metrics["test_size"]))

    results_file.write("\n")
    for epoch in range(0, model.epochs_measured + 1):
        if epoch in metrics:
            results_file.write( "\n  Epoch %u \n" % epoch )
            for dataset in metrics[ epoch ]:
                results_file.write( "%s dataset\n" % dataset )
                for metric in metrics[ epoch ][ dataset ]:
                    results_file.write( metric + "\n")
                    results_file.write( str( metrics[ epoch ][ dataset ][ metric ] ) )
                    results_file.write("\n")
                results_file.write("\n")
    results_file.write("\n")

    results_file.close()
