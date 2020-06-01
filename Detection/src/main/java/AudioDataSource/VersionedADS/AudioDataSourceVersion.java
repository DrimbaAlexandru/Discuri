package AudioDataSource.VersionedADS;

import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.CachedADS.CachedAudioDataSource;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import AudioDataSource.FileADS.FileAudioSourceFactory;
import AudioDataSource.FileADS.IFileAudioDataSource;
import AudioDataSource.IAudioDataSource;
import ProjectManager.ProjectStatics;
import Utils.Interval;
import Utils.MyPair;
import Utils.Tuple_3;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static Exceptions.DataSourceExceptionCause.CHANNEL_NOT_VALID;
import static Exceptions.DataSourceExceptionCause.IO_ERROR;
import static java.nio.file.Files.deleteIfExists;

/**
 * Created by Alex on 12.12.2017.
 */

class FileToProjectMapping
{
    final String file_name;
    final Interval project_interval;
    final Interval file_interval;

    FileToProjectMapping( String f, int prj_fs, int f_fs, int len )
    {
        file_name = f;
        project_interval = new Interval( prj_fs, len );
        file_interval = new Interval( f_fs, len );
    }

    int get_length()
    {
        return project_interval.r-project_interval.l;
    }

    @Override
    public String toString()
    {
        return project_interval.l + " -> " + project_interval.r + " @" + file_name + ": " + file_interval.l + "->" + file_interval.r;
    }

    public boolean follows( FileToProjectMapping other )
    {
        return ( ( file_name.equals( other.file_name ) )
                 && ( project_interval.l == other.project_interval.r )
                 && ( file_interval.l == other.file_interval.r ) );
    }
}

public class AudioDataSourceVersion implements IAudioDataSource
{
    private int sample_rate;
    private int channel_number;
    private int version;
    private int sample_number;

    /* We need two different ADS variables, because the writes shall be made in full sequential chunk files and shall not be affected by reads. */
    private IFileAudioDataSource r_fileAudioDataSource = null;
    private IFileAudioDataSource w_fileAudioDataSource = null;

    private ArrayList< FileToProjectMapping > mapping = new ArrayList<>();

    public AudioDataSourceVersion( int version, int sample_rate, int channel_number, int sample_number )
    {
        this.sample_number = sample_number;
        this.channel_number = channel_number;
        this.version = version;
        this.sample_rate = sample_rate;
    }

    public AudioDataSourceVersion( int version, String file, boolean is_temp ) throws DataSourceException
    {
        this.version = version;

        /* Call with null is_temp, as the entry shall already exist. */
        FileADSManager.associate_file_with_version( file, is_temp, version );
        r_fileAudioDataSource = FileADSManager.get_file_ADS( file );

        sample_number = r_fileAudioDataSource.get_sample_number();
        channel_number = r_fileAudioDataSource.get_channel_number();
        sample_rate = r_fileAudioDataSource.get_sample_rate();
        map( 0, sample_number, 0, file );
    }

    public AudioDataSourceVersion( AudioDataSourceVersion other ) throws DataSourceException
    {
        this.version = other.version + 1;
        this.sample_number = other.sample_number;
        this.sample_rate = other.sample_rate;
        this.channel_number = other.channel_number;
        this.r_fileAudioDataSource = other.r_fileAudioDataSource;
        this.w_fileAudioDataSource = other.w_fileAudioDataSource;

        for( FileToProjectMapping m : other.mapping )
        {
            this.mapping.add( new FileToProjectMapping( m.file_name, m.project_interval.l, m.file_interval.l, m.project_interval.get_length() ) );

            /* Call with null is_temp, as the entry shall already exist. */
            FileADSManager.associate_file_with_version( m.file_name, null, version );
        }
    }

    private void move_project_mapping( int first_project_sample_index, int amount )
    {
        int left_margin;
        if( amount == 0 )
        {
            return;
        }
        if( amount < 0 )
        {
            left_margin = first_project_sample_index + amount;
        }
        else
        {
            left_margin = first_project_sample_index;
        }

        for( int i = 0; i < mapping.size(); i++ )
        {
            FileToProjectMapping map = mapping.get( i );
            if( map.project_interval.l >= left_margin )
            {
                mapping.set( i, new FileToProjectMapping( map.file_name, map.project_interval.l + amount, map.file_interval.l + amount, map.get_length() ) );
            }
        }
        sample_number += amount;
    }

    private void map( int proj_start_sample_index, int length, int file_start_sample_index, String file_path ) throws DataSourceException
    {
        int index = 0;
        for( FileToProjectMapping m : mapping )
        {
            if( m.project_interval.l >= proj_start_sample_index )
            {
                break;
            }
            index++;
        }
        sample_number = Math.max( sample_number, proj_start_sample_index + length );
        mapping.add( index, new FileToProjectMapping( file_path, proj_start_sample_index, file_start_sample_index, length ) );
        FileADSManager.associate_file_with_version( file_path, null, version );
    }

    private void demap( int proj_start_sample_index, int length ) throws DataSourceException
    {
        if( length == 0 )
        {
            return;
        }

        Interval demap_interval = new Interval( proj_start_sample_index, length );
        HashSet< String > demapped_files = new HashSet<>();

        split_map( proj_start_sample_index );
        split_map( proj_start_sample_index + length );

        for( Iterator< FileToProjectMapping > iterator = mapping.iterator(); iterator.hasNext(); )
        {
            FileToProjectMapping map = iterator.next();
            if( map.project_interval.getIntersection( demap_interval ) != null )
            {
                iterator.remove();
                demapped_files.add( map.file_name );
            }
        }
        if( proj_start_sample_index + length > sample_number )
        {
            sample_number = proj_start_sample_index;
        }

        /* Deassociate the files that are no longer referenced by this version */
        for( String file : demapped_files )
        {
            boolean found = false;
            for( FileToProjectMapping map : mapping )
            {
                if( map.file_name.equals( file ) )
                {
                    found = true;
                    break;
                }
            }
            if( !found )
            {
                FileADSManager.deassociate_file_from_version( file, version );
            }
        }
    }

    private void split_map( int proj_sample_index ) throws DataSourceException
    {
        FileToProjectMapping map = null;
        int left_length = 0;
        for( Iterator< FileToProjectMapping > iterator = mapping.iterator(); iterator.hasNext(); )
        {
            map = iterator.next();
            if( map.project_interval.contains( proj_sample_index ) )
            {
                left_length = proj_sample_index - map.project_interval.l;
                if( left_length == 0 || left_length == map.get_length() )
                {
                    break;
                }
                iterator.remove();
                break;
            }
        }
        if( left_length != 0 )
        {
            map( map.project_interval.l, left_length, map.file_interval.l, map.file_name );
            map( proj_sample_index, map.get_length() - left_length, map.file_interval.l + left_length, map.file_name );
        }

    }

    private void compact_mapping()
    {
        FileToProjectMapping map, next_map;
        int i;
        for( i = 0; i < mapping.size() - 1; i++ )
        {
            map = mapping.get( i );
            next_map = mapping.get( i + 1 );
            if( next_map.follows( map ) ) //Concatenate
            {
                mapping.set( i, new FileToProjectMapping( map.file_name, map.project_interval.l, map.file_interval.l, map.get_length() + next_map.get_length() ) );
                mapping.remove( i + 1 );
                i--;
            }
        }
    }

    private FileToProjectMapping get_mapping( int project_index )
    {
        for( FileToProjectMapping map : mapping )
        {
            if( map.project_interval.contains( project_index ) )
            {
                return map;
            }
        }
        return null;
    }

    public void close() throws DataSourceException
    {
        demap( 0, Integer.MAX_VALUE );
    }

    @Override
    public int get_channel_number()
    {
        return channel_number;
    }

    @Override
    public int get_sample_number()
    {
        return sample_number;
    }

    @Override
    public int get_sample_rate()
    {
        return sample_rate;
    }

    @Override
    public AudioSamplesWindow get_samples( int first_sample_index, int length ) throws DataSourceException
    {
        float buf[][] = null;
        int i, j, k;

        first_sample_index = Math.min( first_sample_index, get_sample_number() );
        length = Math.min( length, get_sample_number() - first_sample_index );

        for( i = 0; i < length; )
        {
            FileToProjectMapping map = get_mapping( i + first_sample_index );
            int temp_len;
            int file_first_sample_index;
            AudioSamplesWindow win;

            if( map == null )
            {
                throw new DataSourceException( "Sample index not mapped", DataSourceExceptionCause.SAMPLE_NOT_CACHED );
            }

            if( r_fileAudioDataSource == null || !r_fileAudioDataSource.getFile_path().equals( map.file_name ) )
            {
                r_fileAudioDataSource = FileADSManager.get_file_ADS( map.file_name );
            }
            temp_len = Math.min( length - i, map.project_interval.r - i - first_sample_index );
            file_first_sample_index = i + first_sample_index - map.project_interval.l + map.file_interval.l;
            win = r_fileAudioDataSource.get_samples( file_first_sample_index, temp_len );

            if( temp_len == length )
            {
                win.getInterval().l = first_sample_index;
                win.getInterval().r = first_sample_index + length;
                return win;
            }
            if( buf == null )
            {
                buf = new float[ channel_number ][ length ];
            }
            for( k = 0; k < channel_number; k++ )
            {
                for( j = 0; j < temp_len; j++ )
                {
                    buf[ k ][ j + i ] = win.getSample( file_first_sample_index + j, k );
                }
            }
            i += temp_len;
        }
        if( buf == null )
        {
            buf = new float[ channel_number ][ length ];
        }
        return new AudioSamplesWindow( buf, first_sample_index, length, channel_number );
    }

    @Override
    public void put_samples( AudioSamplesWindow new_samples ) throws DataSourceException
    {
        replace_block( new_samples, new_samples.get_length() );
    }

    public void replace_block( AudioSamplesWindow samples, int replaced_length ) throws DataSourceException
    {
        if( samples.get_channel_number() != channel_number )
        {
            throw new DataSourceException( "Project and window channel numbers do not match", CHANNEL_NOT_VALID );
        }

        demap( samples.get_first_sample_index(), replaced_length );
        move_project_mapping( samples.get_first_sample_index(), samples.get_length() - replaced_length );

        int index = samples.get_first_sample_index();
        int temp_len;

        while( index < samples.get_first_sample_index() + samples.get_length() )
        {
            /* If there's no write ADS, or if the current one is full, create a new chunk file */
            if( w_fileAudioDataSource == null || w_fileAudioDataSource.get_sample_number() >= ProjectStatics.get_temp_file_max_samples() )
            {
                /* Create and immediately release the file handle */
                IFileAudioDataSource new_chunk_file_ADS = FileAudioSourceFactory.createFile( ProjectStatics.get_temp_files_path() + FileADSManager.gimme_a_new_files_name(), samples.get_channel_number(), sample_rate, 4 );
                new_chunk_file_ADS.close();

                FileADSManager.associate_file_with_version( new_chunk_file_ADS.getFile_path(), true, version );
                w_fileAudioDataSource = FileADSManager.get_file_ADS( new_chunk_file_ADS.getFile_path() );
            }
            temp_len = Math.min( samples.get_first_sample_index() + samples.get_length() - index, ProjectStatics.get_temp_file_max_samples() - w_fileAudioDataSource.get_sample_number() );

            float buf[][] = new float[ samples.get_channel_number() ][ temp_len ];
            for( int i = 0; i < temp_len; i++ )
            {
                for( int k = 0; k < channel_number; k++ )
                {
                    buf[ k ][ i ] = samples.getSample( i + index, k );
                }
            }
            AudioSamplesWindow win = new AudioSamplesWindow( buf, w_fileAudioDataSource.get_sample_number(), temp_len, channel_number );
            w_fileAudioDataSource.put_samples( win );
            map( index, temp_len, w_fileAudioDataSource.get_sample_number() - temp_len, w_fileAudioDataSource.getFile_path() );

            index += temp_len;
        }

        compact_mapping();
        sample_number += samples.get_length() - replaced_length;

    }

    public int getVersion()
    {
        return version;
    }
}
