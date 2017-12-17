package AudioDataSource.VersionedAudioDataSource;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import AudioDataSource.FileADS.AUFileAudioSource;
import AudioDataSource.IAudioDataSource;
import AudioDataSource.ADCache.AudioSamplesWindow;
import Utils.Interval;
import sun.security.x509.CertificateValidity;

import java.util.*;

import static AudioDataSource.Exceptions.DataSourceExceptionCause.CHANNEL_NOT_VALID;

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
    private AUFileAudioSource fileAudioSource = null;
    private static final int max_samples_per_chunk = 1024 * 512;
    private List< FileToProjectMapping > mapping = new LinkedList<>();

    public AudioDataSourceVersion( int version, int sample_rate, int channel_number, int sample_number )
    {
        this.sample_number = sample_number;
        this.channel_number = channel_number;
        this.version = version;
        this.sample_rate = sample_rate;
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
        List< FileToProjectMapping > refs_to_add = new ArrayList<>();
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

        for( Iterator< FileToProjectMapping > it = mapping.iterator(); it.hasNext(); )
        {
            FileToProjectMapping map = it.next();
            if( map.project_interval.l >= left_margin )
            {
                refs_to_add.add( new FileToProjectMapping( map.file_name, map.project_interval.l + amount, map.file_interval.l + amount, map.get_length() ) );
                it.remove();
            }
        }
        mapping.addAll( refs_to_add );
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
        FileToProjectMapping map;
        int left_length;
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
                ProjectFilesManager.deassociate_file_from_version( map.file_name, version );
                map( map.project_interval.l, left_length, map.file_interval.l, map.file_name );
                map( proj_sample_index, map.get_length() - left_length, map.file_interval.l + left_length, map.file_name );
                break;
            }
        }

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
        return null;
    }

    @Override
    public AudioSamplesWindow get_resized_samples( int first_sample_index, int length, int resized_length ) throws DataSourceException
    {
        throw new DataSourceException( DataSourceExceptionCause.METHOD_NOT_SUPPORTED );
    }

    @Override
    public void put_samples( AudioSamplesWindow new_samples ) throws DataSourceException
    {
        throw new DataSourceException( DataSourceExceptionCause.METHOD_NOT_SUPPORTED );
    }

    public void replace_block( AudioSamplesWindow samples, int replaced_length ) throws DataSourceException
    {
        if( samples.getChannel_number() != channel_number )
        {
            throw new DataSourceException( "Project and window channel numbers do not match", CHANNEL_NOT_VALID );
        }

        demap( samples.getFirst_sample_index(), replaced_length );
        move_project_mapping( samples.getFirst_sample_index(), samples.getSample_number() - replaced_length );

        int index = samples.getFirst_sample_index();
        int temp_len;

        while( index < samples.getFirst_sample_index() + samples.getSample_number() )
        {
            if( fileAudioSource == null || fileAudioSource.get_sample_number() >= max_samples_per_chunk )
            {
                fileAudioSource = new AUFileAudioSource( ProjectFilesManager.gimme_a_new_files_name(), samples.getChannel_number(), sample_rate, 2 );
            }
            temp_len = Math.min( samples.getFirst_sample_index() + samples.getSample_number() - index, max_samples_per_chunk - fileAudioSource.get_sample_number() );

            double buf[][] = new double[ samples.getChannel_number() ][ temp_len ];
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

    }
}
