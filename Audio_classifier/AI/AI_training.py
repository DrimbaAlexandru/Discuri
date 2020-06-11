import datetime

from AI.AI_1D_classifier import BinaryClassifierModelWithGenerator
from AI.AI_utils import *

# Set some parameters
TRAIN_PATH = 'e:\\datasets\\257-1 new alg\\'
TEST_PATH = None
TEST_DATA_LABELED = True

BATCH_SIZE = 1024

model = BinaryClassifierModelWithGenerator( INPUT_SIZE,
                                            OUTPUT_SIZE,
                                            OFFSET,
                                            SAMPLE_RATE,
                                            #"96000_"+str(INPUT_SIZE)+"_"+str(OUTPUT_SIZE)+"_" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S") +".h5",
                                            "96000_257_1_20200609-210550.h5",
                                            BATCH_SIZE,
                                            TRAIN_PATH,
                                            TEST_PATH,
                                            True,
                                            TEST_DATA_LABELED)

#model.create_model()
model.load_model()

model.fit_model( 70 )
