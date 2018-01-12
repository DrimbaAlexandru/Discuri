package AudioDataSource.VersionedADS;

import AudioDataSource.Exceptions.DataSourceException;

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
        AudioDataSourceVersion adsv = new AudioDataSourceVersion( 0, path );
        versions.add( adsv );
        current_version = 0;
    }

    public VersionedAudioDataSource( int sample_rate, int channel_number, int sample_number ) throws DataSourceException
    {
        AudioDataSourceVersion adsv = new AudioDataSourceVersion( 0, sample_rate, channel_number, sample_number );
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

    public AudioDataSourceVersion create_new()
    {
        while( versions.size() > current_version + 1 )
        {
            versions.get( current_version + 1 ).destroy();
            versions.remove( versions.get( current_version + 1 ) );
        }
        AudioDataSourceVersion ver = versions.get( current_version ).duplicate();
        current_version++;
        versions.add( current_version, ver );
        return ver;
    }

    public void dispose()
    {
        current_version = versions.size() - 1;
        while( versions.size() > 1 )
        {
            versions.get( current_version ).destroy();
            versions.remove( versions.get( current_version ) );

        }
    }

    public int get_number_of_versions()
    {
        return current_version;
    }
}
