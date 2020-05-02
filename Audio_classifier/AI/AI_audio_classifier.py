

#----------------------------------------------
# Global constants
#----------------------------------------------
AI_AUDIO_SAMPLE_RATE = 96000
AI_AUDIO_INPUTS = 129
AI_AUDIO_OUTPUTS = 1
AI_AUDIO_OFFSET = 65

def AI_audio_classify( samples ):
    return  b'\x00' * ( len(samples) - AI_AUDIO_INPUTS + AI_AUDIO_OUTPUTS )
