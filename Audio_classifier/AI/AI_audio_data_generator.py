import numpy as np
from keras.utils import Sequence

import AI.AI_utils as utils


class MarkedAudioDataGenerator(Sequence):
    """Generates data for Keras
    Sequence based data generator. Suitable for building data generator for training and prediction.
    """
    def __init__(self, inputs, outputs, offset, items, batch_size,
                 to_fit = True, shuffle=True ):
        """Initialization
        :param file_list: list of all marked signal filenames to use in the generator
        :param base_path: base path of file_list
        :param to_fit: True to return X and y, False to return X only
        :param batch_size: batch size at each iteration
        :param shuffle: True to shuffle label indexes after every epoch
        """
        self.to_fit = to_fit
        self.batch_size = batch_size
        self.shuffle = shuffle
        self.input_cnt = inputs
        self.output_cnt = outputs
        self.mark_offset = offset
        self.items = items

        self.on_epoch_end()


    def __len__(self):
        """Denotes the number of batches per epoch
        :return: number of batches per epoch
        """
        return len( self.items )


    def __getitem__( self, index ):
        """Generate one batch of data
        :param index: index of the batch
        :return: X and y when fitting. X only when predicting
        """

        this_file, start_idx = self.items[ self.indexes[ index ] ]

        # Generate data
        X, y = utils.load_marked_signal_file( this_file, start_idx, self.batch_size )

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
