

#----------------------------------------------
# Global constants
#----------------------------------------------
AI_AUDIO_SAMPLE_RATE = 96000
AI_AUDIO_INPUTS = 129
AI_AUDIO_OUTPUTS = 1
AI_AUDIO_OFFSET = 64

def AI_audio_classify( sample_rate, samples ):
    return [ 1.0 ] + [ 0.0 ] * ( len(samples) - 2 - AI_AUDIO_INPUTS + AI_AUDIO_OUTPUTS ) +  [ 1.0 ]
