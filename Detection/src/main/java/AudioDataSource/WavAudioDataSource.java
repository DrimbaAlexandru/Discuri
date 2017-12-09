package AudioDataSource;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import WavFile.BasicWavFile.BasicWavFile;
import WavFile.BasicWavFile.BasicWavFileException;
import WavFile.AudioDataCache.AudioSamplesWindow;

import java.io.File;
import java.io.IOException;

/**
 * Created by Alex on 08.12.2017.
 */
public class WavAudioDataSource implements IAudioDataSource
{
    private BasicWavFile wavFile;

    public WavAudioDataSource( File file ) throws IOException, BasicWavFileException
    {
        wavFile = BasicWavFile.openWavFile( file );
    }

    @Override
    public int get_channel_number()
    {
        return wavFile.getNumChannels();
    }

    @Override
    public int get_sample_number()
    {
        return ( int )wavFile.getNumFrames();
    }

    @Override
    public int get_sample_rate()
    {
        return ( int )wavFile.getSampleRate();
    }

    @Override
    public AudioSamplesWindow get_samples( int first_sample_index, int length ) throws DataSourceException
    {
        double buffer[][] = new double[ get_channel_number() ][ length ];
        try
        {
            wavFile.set_stream_position( first_sample_index, 0 );
            wavFile.readFrames( buffer, length );
            return new AudioSamplesWindow( buffer, first_sample_index, length, get_channel_number() );
        }
        catch( IOException e )
        {
            throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.IO_ERROR );
        }
        catch( BasicWavFileException e )
        {
            throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.GENERIC_ERROR );
        }
    }

    @Override
    public AudioSamplesWindow get_resized_samples( int first_sample_index, int length, int resized_length ) throws DataSourceException
    {
        return null;
    }

    @Override
    public void put_samples( AudioSamplesWindow new_samples ) throws DataSourceException
    {

    }

}
