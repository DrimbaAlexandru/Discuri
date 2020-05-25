import os
import random
import warnings
import datetime

from skimage.transform import resize, rescale

from keras.models import Model, load_model
from keras.layers import Input
from keras.layers.core import Dropout, Lambda, Dense
from keras.layers.convolutional import Conv1D
from keras.layers.pooling import MaxPooling1D
from keras.layers.merge import concatenate
from keras.callbacks import EarlyStopping, ModelCheckpoint
from keras.utils import Sequence
from keras import backend as K, Sequential

import numpy as np

from AI.AI_audio_data_generator import MarkedAudioDataGenerator


warnings.filterwarnings('ignore', category=UserWarning, module='skimage')
seed = 42
random.seed = seed
np.random.seed = seed

# m * x * y * z* n
# m - number of images
# x, y, z - image dimensions
# n - number of classes
def iou_coef(y_true, y_pred, smooth=1):
    intersection = K.sum(y_true * y_pred, axis=[1,2,3,4])
    union = K.sum(y_true,[1,2,3,4])+K.sum(y_pred,[1,2,3,4])-intersection
    iou = K.mean((intersection + smooth) / (union + smooth), axis=0)
    return iou


def dice_coef(y_true, y_pred, smooth=1):
    intersection = K.sum(y_true * y_pred, axis=[1,2,3,4])
    union = K.sum(y_true, axis=[1,2,3,4]) + K.sum(y_pred, axis=[1,2,3,4])
    dice = K.mean((2. * intersection + smooth)/(union + smooth), axis=0)
    return dice


def iou_coef_loss(yt,yp,smooth=1):
    return 1 - iou_coef(yt,yp,smooth)


def dice_coef_loss(yt,yp,smooth=1):
    return 1 - dice_coef(yt,yp,smooth)


class BinaryClassifierModelWithGenerator:
    def __init__( self,
                  inputs,
                  outputs,
                  offset,
                  sample_rate,
                  train_path_in = None,     # Folder containing multiple signal files
                  test_path_in = None,      # Folder containing multiple signal files
                  # train_path_out = None,
                  # test_path_out = None,
                  test_data_labeled = None,
                  ):

        self.INPUTS = inputs
        self.OUTPUTS = outputs
        self.OFFSET = offset
        self.SAMPLE_RATE = sample_rate
        self.TRAIN_PATH_IN = train_path_in
        self.TEST_PATH_IN = test_path_in
        # self.TRAIN_PATH_OUT = train_path_out
        # self.TEST_PATH_OUT = test_path_out

        self.IS_TEST_DATA_LABELED = test_data_labeled

        self.MODEL_PATH = "./1d_classifier_model_" + str( self.SAMPLE_RATE ) + ".h5"
        self.LOG_DIR = "./logs/fit//" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S")

        self.VALIDATION_SPLIT = 0.5

        self.epochs_measured = 0

        self.predict_only = train_path_in is None \
                            or test_path_in is None \
                            or test_data_labeled is None

        if self.predict_only:
            return

        # Get training data files
        # Returns a list of file names in the training path
        training_files = next( os.walk( self.TRAIN_PATH_IN ) )[2]
        #np.random.shuffle( training_files )
        validation_files = training_files[ :int( len( training_files ) * self.VALIDATION_SPLIT ) ]
        learning_files = training_files[ int( len( training_files ) * self.VALIDATION_SPLIT ): ]

        # Get test IDs
        # Returns a list of file names in the testing path
        testing_files = next( os.walk( self.TEST_PATH_IN ) )[2]

        self.learning_generator = MarkedAudioDataGenerator( learning_files,
                                                            self.TRAIN_PATH_IN,
                                                            self.INPUTS,
                                                            self.OUTPUTS,
                                                            self.OFFSET,
                                                            True,
                                                            self.SAMPLE_RATE )
        self.validation_generator = MarkedAudioDataGenerator( validation_files,
                                                              self.TRAIN_PATH_IN,
                                                              self.INPUTS,
                                                              self.OUTPUTS,
                                                              self.OFFSET,
                                                              True,
                                                              self.SAMPLE_RATE )
        self.test_generator = MarkedAudioDataGenerator( testing_files,
                                                        self.TRAIN_PATH_IN,
                                                        self.INPUTS,
                                                        self.OUTPUTS,
                                                        self.OFFSET,
                                                        True,
                                                        self.SAMPLE_RATE )

    def save_model( self ):
        # Save the model
        self.model.save( self.MODEL_PATH )

    def load_model( self ):
        self.model = load_model( self.MODEL_PATH, custom_objects={'iou_coef_loss': iou_coef_loss,
                                                                  'dice_coef_loss': dice_coef_loss,
                                                                  'iou_coef': iou_coef,
                                                                  'dice_coef': dice_coef,
                                                                  'metrics': [ iou_coef, dice_coef, iou_coef_loss, dice_coef_loss, "accuracy" ]} )

    def create_model( self ):
        print( "Build model" )

        # Build U-Net model
        self.model = Sequential()
        # self.model.add( Dense( 64, input_shape= ( self.INPUTS, ) ) )
        # self.model.add( Dense( 32 ) )
        # self.model.add( Dense( self.OUTPUTS ) )
        self.model.add( Conv1D( filters = 16, kernel_size = 32, strides = 2, activation = 'relu', input_shape = ( 1, self.INPUTS ), data_format = "channels_first" ) )
        self.model.add( Conv1D( filters = 8, kernel_size = 16, activation = 'relu', data_format = "channels_first"  ) )
        self.model.add( Dropout( 0.1 ) )
        self.model.add( Conv1D( filters = 4, kernel_size = 8, activation = 'relu', data_format = "channels_first" ) )
        self.model.add( Dropout( 0.05 ) )
        self.model.add( Conv1D( filters = 1, kernel_size = 4, activation = 'relu', data_format = "channels_first" ) )
        self.model.add( MaxPooling1D( pool_size= 1, data_format = "channels_first" ) )
        self.model.add( Dense( self.OUTPUTS, activation='sigmoid' ) )
        print( self.model.output_shape )

        #self.model.compile(optimizer='adam', loss=iou_coef_loss, metrics=[ iou_coef_loss, dice_coef_loss, "accuracy" ])
        self.model.compile(optimizer='adam', loss="binary_crossentropy", metrics=[ "accuracy" ] )
        self.model.summary()

    def fit_model( self, epochs = 50 ):
        print( "Fit model" )

        if self.predict_only:
            print("Not possible in predict only mode")
            return

        # Fit model
        earlystopper = EarlyStopping(patience=5, verbose=1, monitor="accuracy")
        checkpointer = ModelCheckpoint(self.MODEL_PATH, verbose=1, save_best_only=True, monitor="accuracy")
        # checkpointer = ModelCheckpoint( self.MODEL_PATH, verbose=1, save_best_only=True )

        self.model.fit_generator(generator=self.learning_generator, epochs=epochs,
                                 callbacks=[earlystopper, checkpointer],
                                 validation_data=self.validation_generator)

        self.epochs_measured += epochs

    def predict_markings( self, input_signal ):
        length = len( input_signal )
        i = 0
        preds = []

        while( i < length - self.INPUTS ):
            pred = self.model.predict( input_signal[ i : i + self.INPUTS ] , verbose=1)
            preds = preds + pred
            i += self.OUTPUTS

        return preds
