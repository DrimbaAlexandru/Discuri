import numpy as np
import random

# Set some parameters
from AI.AI_1D_classifier import BinaryClassifierModelWithGenerator
from AI.AI_utils import evaluate_model_generator, write_model_metrics

TRAIN_PATH = './training_data/'
TEST_PATH = './test_data/'
TEST_DATA_LABELED = True

INPUT_SIZE = 129
OUTPUT_SIZE = 1
OFFSET = 64
SAMPLE_RATE = 96000

model = BinaryClassifierModelWithGenerator( INPUT_SIZE,
                                            OUTPUT_SIZE,
                                            OFFSET,
                                            SAMPLE_RATE,
                                            TRAIN_PATH,
                                            TEST_PATH,
                                            TEST_DATA_LABELED)
metrics = {}

model.create_model()
#model.load_model()
for i in range( 0, 10 ):
    model.fit_model( 5 )
    model.save_model()
    #model.predict_from_model()
    evaluate_model_generator(model,metrics)
write_model_metrics(model,metrics)
