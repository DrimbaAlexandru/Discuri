import datetime

from AI.AI_1D_classifier import BinaryClassifierModelWithGenerator
from AI.AI_utils import *

# Set some parameters
TRAIN_PATH = 'e:\\datasets\\257-1 reduced\\'
TEST_PATH = None
TEST_DATA_LABELED = True

BATCH_SIZE = 10000

model = BinaryClassifierModelWithGenerator( INPUT_SIZE,
                                            OUTPUT_SIZE,
                                            OFFSET,
                                            SAMPLE_RATE,
                                            #96000_"+str(INPUT_SIZE)+"_"+str(OUTPUT_SIZE)+"_" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S") +".h5",
                                            "1d_classifier_model_96000_257_1_20200604-202223 150 epochs + weights.h5",
                                            BATCH_SIZE,
                                            TRAIN_PATH,
                                            TEST_PATH,
                                            True,
                                            TEST_DATA_LABELED)
metrics = {}

#model.create_model()
model.load_model()

for i in range( 0, 1 ):
    #model.fit_model( 10 )
    #model.save_model()
    if( ( i + 1 ) % 1 == 0 ):
        evaluate_model_generator(model,metrics)
        write_model_metrics(model,metrics)
