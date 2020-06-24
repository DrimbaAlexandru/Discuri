import os

import numpy as np

from AI.AI_1D_classifier import BinaryClassifierModelWithGenerator
from AI.AI_utils import SAMPLE_VALUE_MUL_FACTOR

#----------------------------------------------
# Global constants
#----------------------------------------------
IOP_AI_STATUS_OK = 0
IOP_AI_STATUS_FAIL = 1
IOP_AI_STATUS_MODEL_UNAVAILABLE = 2

#             Sample rate : path                                  IN   OUT  OFFSET
CLASSIFIER_DICT = { 96000: ("reg_96000_257_1_20200624-131755.h5", 257, 1, 128 ) }
model = None
success = False

def AI_load_classifier( sample_rate ):
    global model, success

    if sample_rate not in CLASSIFIER_DICT:
        success = False
        return IOP_AI_STATUS_MODEL_UNAVAILABLE

    path, inputs, outputs, offset = CLASSIFIER_DICT[ sample_rate ]; path = os.path.dirname(os.path.realpath(__file__)) + "\\" + path

    if( model is None or model.MODEL_PATH != path ):
        model = BinaryClassifierModelWithGenerator( inputs, outputs, offset, sample_rate, path )
        success = model.load_model()

    return IOP_AI_STATUS_OK if success else IOP_AI_STATUS_FAIL

# Returns ( status, sample_rate, inputs, outputs, offset )
def AI_get_properties():
    if model is None:
        return ( IOP_AI_STATUS_MODEL_UNAVAILABLE, None, None, None, None )
    return IOP_AI_STATUS_OK if success else IOP_AI_STATUS_FAIL, model.SAMPLE_RATE, model.INPUTS, model.OUTPUTS, model.OFFSET

# Returns ( status, mark_probabilities )
def AI_audio_classify( samples ):
    global model

    if model is None:
        return ( IOP_AI_STATUS_MODEL_UNAVAILABLE, None )

    float_samples = np.asarray( samples ) / SAMPLE_VALUE_MUL_FACTOR
    return ( IOP_AI_STATUS_OK, model.predict_markings( float_samples ) )

