package AudioDataSource;

import Utils.Exceptions.DataSourceException;

/**
 * Created by Alex on 06.12.2017.
 */
public interface IAudioDataSource
{
    int get_channel_number();
    int get_sample_number();
    int get_sample_rate();
    AudioSamplesWindow get_samples( int first_sample_index, int length ) throws DataSourceException;
    void put_samples( AudioSamplesWindow new_samples ) throws DataSourceException;

    default void limit_sample_number( int sample_number ) throws DataSourceException
    {

    }
    void close() throws DataSourceException;
}
