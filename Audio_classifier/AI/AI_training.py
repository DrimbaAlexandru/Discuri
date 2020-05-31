import numpy as np
import random

# Set some parameters
from AI.AI_1D_classifier import BinaryClassifierModelWithGenerator
from AI.AI_utils import *

TRAIN_PATH = 'e:\\datasets\\384-128 4.86 near 32\\'
TEST_PATH = None
TEST_DATA_LABELED = True

BATCH_SIZE = 2000

model = BinaryClassifierModelWithGenerator( INPUT_SIZE,
                                            OUTPUT_SIZE,
                                            OFFSET,
                                            SAMPLE_RATE,
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
