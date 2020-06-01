import datetime

from AI.AI_1D_classifier import BinaryClassifierModelWithGenerator
from AI.AI_utils import *

# Set some parameters
TRAIN_PATH = 'e:\\datasets\\384-128 4.86 near 32\\'
TEST_PATH = None
TEST_DATA_LABELED = True

BATCH_SIZE = 2000

model = BinaryClassifierModelWithGenerator( INPUT_SIZE,
                                            OUTPUT_SIZE,
                                            OFFSET,
                                            SAMPLE_RATE,
                                            "1d_classifier_model_96000_384_128" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S") +".h5",
                                            BATCH_SIZE,
                                            TRAIN_PATH,
                                            TEST_PATH,
                                            True,
                                            TEST_DATA_LABELED)
metrics = {}

model.create_model()
#model.load_model()

for i in range( 0, 4 ):
    model.fit_model( 5 )
    model.save_model()
    #model.predict_from_model()
    evaluate_model_generator(model,metrics)
write_model_metrics(model,metrics)
