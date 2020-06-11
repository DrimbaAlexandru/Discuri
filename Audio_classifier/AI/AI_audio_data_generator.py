import numpy as np
from keras.utils import Sequence
from sklearn.preprocessing import StandardScaler
from tqdm import tqdm

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
        self.cache = False
        self.to_fit = to_fit
        self.batch_size = batch_size
        self.shuffle = shuffle
        self.input_cnt = inputs
        self.output_cnt = outputs
        self.mark_offset = offset
        self.items = items
        self.on_epoch_end()

        self.X_data = []
        self.Y_data = []

        if self.cache:
            for i in range( 0, len( self.items ) ):
                this_file, start_idx = self.items[ i ]
                X, Y = utils.load_marked_signal_file( this_file, start_idx, self.batch_size )

                self.X_data.append( X )
                self.Y_data.append( Y )

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
        if self.cache:
            X = self.X_data[ self.indexes[ index ] ]
            y = self.Y_data[ self.indexes[ index ] ]
        else:
            X, y = utils.load_marked_signal_file( this_file, start_idx, self.batch_size )

        # Augment the data by varying the input signal amplitude
        factors = np.random.uniform( 0.25, 2.0, X.shape[ 0 ] ) * ( np.random.randint( 0, 2, X.shape[ 0 ] ) * 2 - 1 )
        X = np.asarray( [ X[ i ] * factors[ i ] for i in range( 0, X.shape[ 0 ] ) ] )

        scaler = StandardScaler()
        scaler.fit( X )
        X = scaler.transform( X )

        if self.to_fit:
            return X, y#, self._compute_weights( y )
        else:
            return X

    def get_all_y_true(self):
        y_true = []
        for i in tqdm(range(0, len(self)), ascii=True, desc = "Get all Y true"):
            y_true += [ x for x in self[ i ][ 1 ] ]
        return np.asarray( y_true )


    def _compute_weights( self, y ):
        weights = np.sum( y, axis = ( 1 ) ) / self.output_cnt
        weights = utils.POSITIVE_CLASS_WEIGHT * weights + ( utils.NEGATIVE_CLASS_WEIGHT * ( 1 - weights ) )
        return weights


    def get_item_count( self ):
        return len( self.items )


    def on_epoch_end( self ):
        """Updates indexes after each epoch
        """
        self.indexes = np.arange( self.get_item_count() )
        if self.shuffle == True:
            np.random.shuffle(self.indexes)
