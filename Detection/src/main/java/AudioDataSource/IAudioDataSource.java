package AudioDataSource;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.ADCache.AudioSamplesWindow;

/**
 * Created by Alex on 06.12.2017.
 */
public interface IAudioDataSource
{
    int get_channel_number();
    int get_sample_number();
    int get_sample_rate();
    AudioSamplesWindow get_samples( int first_sample_index, int length ) throws DataSourceException;
    AudioSamplesWindow get_resized_samples( int first_sample_index, int length, int resized_length ) throws DataSourceException;
    void put_samples( AudioSamplesWindow new_samples ) throws DataSourceException;
}
