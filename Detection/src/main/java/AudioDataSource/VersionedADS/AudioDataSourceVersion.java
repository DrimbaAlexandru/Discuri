package AudioDataSource.VersionedADS;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import AudioDataSource.FileADS.FileAudioSourceFactory;
import AudioDataSource.FileADS.IFileAudioDataSource;
import AudioDataSource.IAudioDataSource;
import AudioDataSource.ADCache.AudioSamplesWindow;
import Utils.Interval;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static AudioDataSource.Exceptions.DataSourceExceptionCause.CHANNEL_NOT_VALID;
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

class ProjectFilesManager
{
    public static String base_path = "C:\\Users\\Alex\\Desktop\\proj_files\\";
    private static int file_id = 0;

    private static TreeMap< String, List< Integer > > file_references = new TreeMap<>();

    public static void associate_file_with_version( String file, int version )
    {
        file_references.putIfAbsent( file, new ArrayList<>() );
        file_references.get( file ).add( version );
    }

    public static void deassociate_file_from_version( String file, int version )
    {
        List< Integer > assocs = file_references.get( file );
        if( assocs == null )
        {
            return;
        }
        assocs.remove( Integer.valueOf( version ) );
        if( assocs.size() == 0 )
        {
            file_references.remove( file );
            try
            {
                deleteIfExists( new File( file ).toPath() );
            }
            catch( IOException e )
            {
                e.printStackTrace();
            }
            //TODO: Reomve file from Hard Disk
        }
    }

    public static String gimme_a_new_files_name()
    {
        return base_path + "chunk_" + String.format( "%06d", file_id++ ) + ".au";
    }
}

public class AudioDataSourceVersion implements IAudioDataSource
{
    private int sample_rate;
    private int channel_number;
    private int version;
    private int sample_number;
    private IFileAudioDataSource fileAudioSource = null;
    private static final int max_samples_per_chunk = 1024 * 1024;
    private ArrayList< FileToProjectMapping > mapping = new ArrayList<>();

    public AudioDataSourceVersion( int version, int sample_rate, int channel_number, int sample_number )
    {
        this.sample_number = sample_number;
        this.channel_number = channel_number;
        this.version = version;
        this.sample_rate = sample_rate;
    }

    public AudioDataSourceVersion( int version, String file ) throws DataSourceException
    {
        this.version = version;
        fileAudioSource = FileAudioSourceFactory.fromFile( file );
        sample_number = fileAudioSource.get_sample_number();
        channel_number = fileAudioSource.get_channel_number();
        sample_rate = fileAudioSource.get_sample_rate();
        map( 0, sample_number, 0, file );
    }

    public AudioDataSourceVersion duplicate()
    {
        AudioDataSourceVersion other = new AudioDataSourceVersion( version + 1, sample_rate, channel_number, sample_number );
        for( FileToProjectMapping m : mapping )
        {
            other.mapping.add( m );
        }
        return other;
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

    private void map( int proj_start_sample_index, int length, int file_start_sample_index, String file_path )
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
        ProjectFilesManager.associate_file_with_version( file_path, version );
    }

    private void demap( int proj_start_sample_index, int length )
    {
        if( length == 0 )
        {
            return;
        }

        Interval demap_interval = new Interval( proj_start_sample_index, length );

        split_map( proj_start_sample_index );
        split_map( proj_start_sample_index + length );

        for( Iterator< FileToProjectMapping > iterator = mapping.iterator(); iterator.hasNext(); )
        {
            FileToProjectMapping map = iterator.next();
            if( map.project_interval.getIntersection( demap_interval ) != null )
            {
                iterator.remove();
                ProjectFilesManager.deassociate_file_from_version( map.file_name, version );
            }
        }
        if( proj_start_sample_index + length > sample_number )
        {
            sample_number = proj_start_sample_index;
        }

    }

    private void split_map( int proj_sample_index )
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
            ProjectFilesManager.deassociate_file_from_version( map.file_name, version );
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

    public void destroy()
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
        double buf[][] = null;
        int i, j, k;
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
            if( fileAudioSource == null || !fileAudioSource.getFile_path().equals( map.file_name ) )
            {
                fileAudioSource = FileAudioSourceFactory.fromFile( map.file_name );
            }
            temp_len = Math.min( length - i, map.get_length() );
            file_first_sample_index = i + first_sample_index - map.project_interval.l + map.file_interval.l;
            win = fileAudioSource.get_samples( file_first_sample_index, temp_len );

            if( temp_len == length )
            {
                return win;
            }
            if( buf == null )
            {
                buf = new double[ channel_number ][ length ];
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
            if( fileAudioSource == null || fileAudioSource.get_sample_number() >= max_samples_per_chunk )
            {
                fileAudioSource = FileAudioSourceFactory.createFile( ProjectFilesManager.gimme_a_new_files_name(), samples.get_channel_number(), sample_rate, 2 );
            }
            temp_len = Math.min( samples.get_first_sample_index() + samples.get_length() - index, max_samples_per_chunk - fileAudioSource.get_sample_number() );

            double buf[][] = new double[ samples.get_channel_number() ][ temp_len ];
            for( int i = 0; i < temp_len; i++ )
            {
                for( int k = 0; k < channel_number; k++ )
                {
                    buf[ k ][ i ] = samples.getSample( i + index, k );
                }
            }
            AudioSamplesWindow win = new AudioSamplesWindow( buf, fileAudioSource.get_sample_number(), temp_len, channel_number );
            fileAudioSource.put_samples( win );
            map( index, temp_len, fileAudioSource.get_sample_number() - temp_len, fileAudioSource.getFile_path() );

            index += temp_len;
        }

        compact_mapping();

    }

    public int getVersion()
    {
        return version;
    }
}
