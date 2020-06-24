import os
import struct

import numpy as np
from sklearn.metrics import classification_report

OFFSET = 128
OUTPUT_SIZE = 1
INPUT_SIZE = OFFSET * 2 + OUTPUT_SIZE
SAMPLE_RATE = 96000

RECORD_FORMAT = "<" + INPUT_SIZE * "h" + OUTPUT_SIZE * "h"
SAMPLE_VALUE_MUL_FACTOR = 2**15
MAX_SAMPLE_VALUE = 2**15 - 1
MIN_SAMPLE_VALUE = -2**15
RECORD_SIZE = struct.calcsize( RECORD_FORMAT )

POSITIVE_CLASS_WEIGHT = 1
NEGATIVE_CLASS_WEIGHT = 1 / np.sqrt( POSITIVE_CLASS_WEIGHT )
POSITIVE_CLASS_WEIGHT = np.sqrt( POSITIVE_CLASS_WEIGHT )



def load_marked_signal_file( file_path, start_idx, seq_len ):
    with open( file_path, mode='rb') as file:
        file.seek( start_idx * RECORD_SIZE )
        file_content = file.read( seq_len * RECORD_SIZE )
        assert len( file_content ) % RECORD_SIZE == 0, "File: " + file_path + ", start idx: " + str( start_idx ) + ", len: " + str( len( file_content ) )
        record_cnt = len( file_content ) // RECORD_SIZE

        samples = np.zeros( ( record_cnt, INPUT_SIZE, 1 ), dtype=np.float_ )
        markings = np.zeros( ( record_cnt, OUTPUT_SIZE, 1 ), dtype=np.float_ )

        for idx in range( 0, record_cnt ):
            record = struct.unpack( RECORD_FORMAT, file_content[ idx * RECORD_SIZE : ( idx + 1 ) * RECORD_SIZE ] )
            inputs = np.asarray( record[ :INPUT_SIZE ], dtype=np.float_ )
            inputs.shape = ( INPUT_SIZE, 1 )
            samples[ idx ] = inputs / SAMPLE_VALUE_MUL_FACTOR
            outputs = np.asarray( record[ INPUT_SIZE: ], dtype=np.int32 )
            outputs.shape = ( OUTPUT_SIZE, 1 )
            markings[ idx ] =  outputs / SAMPLE_VALUE_MUL_FACTOR

    return( samples, markings )


def get_marked_signal_file_length( file_path ):
    return os.path.getsize( file_path ) // RECORD_SIZE


def shuffle_in_unison_scary(a, b):
    rng_state = np.random.get_state()
    np.random.shuffle(a)
    np.random.set_state(rng_state)
    np.random.shuffle(b)