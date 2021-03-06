package AudioDataSource.VersionedADS;

import Utils.Exceptions.DataSourceException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Alex on 12.12.2017.
 */
public class VersionedAudioDataSource
{
    private List< AudioDataSourceVersion > versions = new ArrayList<>();
    private int current_version = 0;

    public AudioDataSourceVersion get_current_version()
    {
        return versions.get( current_version );
    }

    public VersionedAudioDataSource( String path ) throws DataSourceException
    {
        AudioDataSourceVersion adsv = new AudioDataSourceVersion( 0, path, false );
        versions.add( adsv );
        current_version = 0;
    }

    public VersionedAudioDataSource( int sample_rate, int channel_number, int sample_number ) throws DataSourceException
    {
        AudioDataSourceVersion adsv = new AudioDataSourceVersion( sample_rate, channel_number, sample_number );
        versions.add( adsv );
        current_version = 0;
    }

    public void undo()
    {
        if( current_version > 0 )
        {
            current_version--;
        }
    }

    public void redo()
    {
        if( current_version < versions.size() - 1 )
        {
            current_version++;
        }
    }

    public AudioDataSourceVersion create_next_version() throws DataSourceException
    {
        while( versions.size() > current_version + 1 )
        {
            versions.get( versions.size() - 1 ).close();
            versions.remove( versions.get( versions.size() - 1 ) );
        }
        AudioDataSourceVersion ver = new AudioDataSourceVersion( versions.get( current_version ) );
        current_version++;
        versions.add( current_version, ver );
        return ver;
    }

    public void dispose() throws DataSourceException
    {
        current_version = versions.size() - 1;
        while( versions.size() > 0 )
        {
            versions.get( current_version ).close();
            versions.remove( versions.get( current_version ) );
            current_version--;
        }
    }
}
