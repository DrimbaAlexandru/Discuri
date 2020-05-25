import numpy as np
from keras.utils import Sequence

import AI.AI_utils as utils


class MarkedAudioDataGenerator(Sequence):
    """Generates data for Keras
    Sequence based data generator. Suitable for building data generator for training and prediction.
    """
    def __init__(self, file_list, base_path, inputs, outputs, offset,
                 to_fit = True, batch_size = 96000, shuffle=True ):
        """Initialization
        :param file_list: list of all marked signal filenames to use in the generator
        :param base_path: base path of file_list
        :param to_fit: True to return X and y, False to return X only
        :param batch_size: batch size at each iteration
        :param shuffle: True to shuffle label indexes after every epoch
        """
        self.file_list = file_list
        self.base_path = base_path
        self.to_fit = to_fit
        self.batch_size = batch_size
        self.shuffle = shuffle
        self.input_cnt = inputs
        self.output_cnt = outputs
        self.mark_offset = offset
        self.items = []

        for file in file_list:
            file_sample_count = utils.get_marked_signal_file_length( base_path + file ) - ( self.input_cnt - self.output_cnt ) + ( batch_size - 1 )
            batches_per_file = file_sample_count // batch_size
            for start_idx in range( 0, batches_per_file ):
                self.items.append( ( base_path + file, start_idx * batch_size ) )

        self.current_file = ""
        self.current_file_data = None

        self.on_epoch_end()


    def __len__(self):
        """Denotes the number of batches per epoch
        :return: number of batches per epoch
        """
        return len( self.items )


    def _is_marked( self, markings, stidx, endidx ):
        return np.sum( markings[ stidx : endidx ] ) * 2 >= endidx - stidx

    def _get_nearest_mark( self, markings, idx, bwd_look, fwd_look ):
        bwd_pos = None
        fwd_pos = None
        i = idx
        while i >= 0 and i >= idx - bwd_look:
            if markings[ i ] > 0:
                bwd_pos = -i
                break

        i = idx
        while i <= len( markings ) and i <= idx + fwd_look:
            if markings[ i ] > 0:
                fwd_pos = i
                break

        if fwd_pos is None: return bwd_pos
        if bwd_pos is None: return fwd_pos
        if fwd_pos < -bwd_pos:
            return fwd_pos
        else:
            return bwd_pos

    def __getitem__( self, index ):
        """Generate one batch of data
        :param index: index of the batch
        :return: X and y when fitting. X only when predicting
        """

        margin_lookaround_size = 64
        marking_doubling_prob = 0.1
        non_marked_skipping_prob = 0.95

        this_file, start_idx = self.items[ self.indexes[ index ] ]

        # Generate data
        self.current_file_data = utils.load_marked_signal_file( this_file, start_idx, self.batch_size )
        self.current_file = this_file

        current_samples, current_marks = self.current_file_data

        current_marks = current_marks[ self.mark_offset : self.mark_offset + len( current_samples ) - ( self.input_cnt - self.output_cnt ) ]

        length = len( current_marks )
        X = np.zeros( ( length, 1, self.input_cnt ), dtype=np.float_ )
        y = np.zeros( ( length, 1, self.output_cnt ), dtype=np.bool_ )
        i = 0
        while i < length:
            add = 1
            # if self._is_marked( current_marks, i, i + self.output_cnt ) or abs( self._get_nearest_mark( current_marks, i, margin_lookaround_size, margin_lookaround_size ) ):
            #     add += 1 if prob < marking_doubling_prob else 0
            # else:
            #     add -= 1 if prob < non_marked_skipping_prob else 0

            for j in range( 0, add ):
                X[ i ][ 0 ] = current_samples[ i : i + self.input_cnt ]
                if self.to_fit:
                    y[ i ][ 0 ] = current_marks[ i : i + self.output_cnt ]

            i += 1

        if self.to_fit:
            return X, y
        else:
            return X


    def get_item_count( self ):
        return len( self.items )


    def on_epoch_end( self ):
        """Updates indexes after each epoch
        """
        self.indexes = np.arange( self.get_item_count() )
        if self.shuffle == True:
            np.random.shuffle(self.indexes)
