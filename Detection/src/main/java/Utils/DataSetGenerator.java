package Utils;

import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.IAudioDataSource;
import Utils.DataTypes.Interval;
import Utils.Exceptions.DataSourceException;
import Utils.DataStructures.MarkerFile.*;
import ProjectManager.ProjectManager;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/**
 * Created by Alex on 08.01.2018.
 */
public class DataSetGenerator
{
    private static ByteBuffer buf;
    private int input_size;
    private int output_size;
    private int offset;

    private long total_marked_written = 0;
    private long total_unmarked_written = 0;
    private int marked_written = 0;
    private int unmarked_written = 0;
    private float total_avg = 0.0f;
    private double avg = 0.0;

    public DataSetGenerator( int input_size, int output_size, int offset )
    {
        this.input_size = input_size;
        this.offset = offset;
        this.output_size = output_size;
    }

    public void generate( IAudioDataSource dataSource, IAudioDataSource damageSource, Interval interval, String destination_path, int near_window_size, float non_marked_probab, float doubling_probab, float prob_of_skipping_marking ) throws DataSourceException, IOException
    {
        final int fetch_size = dataSource.get_sample_rate() + ( input_size - output_size );

        Random rand = new Random();

        MarkerFile markerFile = ProjectManager.getMarkerFile();
        interval.limit( 0, dataSource.get_sample_number() );
        buf = ByteBuffer.allocate( input_size * 2 + output_size * 2 );
        buf.order( ByteOrder.LITTLE_ENDIAN );

        marked_written = 0;
        unmarked_written = 0;
        avg = 0.0;

        MarkerFile written_markings = new MarkerFile();

        for( Marking m : markerFile.get_all_markings( interval ) )
        {
            if( rand.nextFloat() < prob_of_skipping_marking )
            {
                continue;
            }
            written_markings.addMark( m.get_first_marked_sample(), m.get_last_marked_sample(), m.getChannel() );

            written_markings.addMark( m.get_first_marked_sample() - near_window_size / 2, m.get_first_marked_sample() - 1, m.getChannel() );
            written_markings.addMark( m.get_last_marked_sample() + 1, m.get_last_marked_sample() + near_window_size / 2, m.getChannel() );
        }

        FileOutputStream fos = new FileOutputStream( destination_path );
        BufferedOutputStream bos = new BufferedOutputStream( fos );
        DataOutputStream writer = new DataOutputStream( bos );

        int i, j, ch;
        boolean willWrite;
        AudioSamplesWindow win;
        AudioSamplesWindow damageWin = null;
        float mark_ratio;

        for( i = interval.l; i < interval.r - input_size; )
        {
            win = dataSource.get_samples( i, Math.min( fetch_size, dataSource.get_sample_number() - i ) );
            if( damageSource != null ) damageWin = damageSource.get_samples( i, Math.min( fetch_size, dataSource.get_sample_number() - i ) );

            System.out.println( "Processed " + i / dataSource.get_sample_rate() + " seconds" );

            for( ch = 0; ch < win.get_channel_number(); ch++ )
            {
                /* j is the index of the first output */
                for( j = offset; j < win.get_length() - ( input_size - output_size ) - offset; j++ )
                {
                    willWrite = false;
                    mark_ratio = get_seq_mark_ratio( i + j - near_window_size / 2, near_window_size + output_size, ch, written_markings );

                    if( mark_ratio > 0 )
                    {
                        willWrite = rand.nextFloat() < mark_ratio;
                    }

                    if( !willWrite )
                    {
                        willWrite = ( rand.nextFloat() < non_marked_probab );
                    }

                    if( willWrite )
                    {
//                        if( ( ( marked_written + unmarked_written ) / output_size ) % 50000 == 0 )
//                        {
//                            System.out.println( "Written " + ( marked_written + unmarked_written ) / output_size);
//                            System.out.println( "At sample " + ( i + j ) + "/" + dataSource.get_sample_number() );
//                            System.out.println( "Ratio ( unmarked / marked ): " + ( float )unmarked_written / marked_written );
//                            System.out.println( "Mean average: " + avg / ( marked_written + unmarked_written ) / output_size );
//                            // marked_written = 0;
//                            // unmarked_written = 0;
//                        }

                        write_case( win, damageWin, i + j - offset, ch, markerFile, writer );
                        mark_ratio = get_seq_mark_ratio( i + j, output_size, ch, markerFile );
                        if( mark_ratio >= 0.5f )
                        {
                            while( rand.nextFloat() < doubling_probab )
                            {
                                write_case( win, damageWin, i + j - offset, ch, markerFile, writer );
                            }
                        }
                    }
                }
            }
            i += win.get_length() - ( input_size - output_size );

        }
        writer.close();

        System.out.println( destination_path );
        System.out.println( "Written " + ( marked_written + unmarked_written ) / output_size );
        System.out.println( "Ratio ( unmarked / marked ): " + ( float )unmarked_written / marked_written );
        System.out.println( "Mean average: " + avg / ( marked_written + unmarked_written ) );

        total_marked_written += marked_written;
        total_unmarked_written += unmarked_written;
        total_avg += avg;
    }

    private float get_seq_mark_ratio( int sample_start_idx, int sample_cnt, int ch, MarkerFile mf )
    {
        int marked = 0;
        for( int k = sample_start_idx; k < sample_start_idx + sample_cnt; k++ )
        {
            marked += mf.isMarked( k, ch ) ? 1 : 0;
        }
        return ( 1.0f * marked / sample_cnt );
    }

    private void write_case( AudioSamplesWindow win, AudioSamplesWindow damageWin, int sample_start_idx, int ch, MarkerFile mf, DataOutputStream writer ) throws DataSourceException, IOException
    {
        boolean isMarked;
        float damage = 0.0f;

        buf.rewind();
        for( int k = sample_start_idx; k < sample_start_idx + input_size; k++ )
        {
            buf.putShort( ( short )( win.getSample( k, ch ) * 32768 ) );
        }

        for( int k = sample_start_idx + offset; k < sample_start_idx + offset + output_size; k++ )
        {
            isMarked = mf.isMarked( k, ch );
            if( isMarked )
            {
                marked_written++;
                if( damageWin == null )
                {
                    damage = 1.0f;
                }
                else
                {
                    damage = 0.1f;
                }
            }
            else
            {
                unmarked_written++;
                if( damageWin == null )
                {
                    damage = 0.0f;
                }
            }
            if( damageWin != null )
            {
                damage += damageWin.getSample( k, ch ) * 0.9f;
            }

            avg += damage;

            buf.putShort( ( short )( damage * 32767 ) );
        }
        writer.write( buf.array(), 0, buf.position() );
    }

    public void write_final_results()
    {
        System.out.println( "Written " + ( total_marked_written + total_unmarked_written ) / output_size );
        System.out.println( "Marked: " + ( total_marked_written ) );
        System.out.println( "Unmarked: " + ( total_unmarked_written ) );
        System.out.println( "Ratio ( unmarked / marked ): " + ( float )total_unmarked_written / total_marked_written );
        System.out.println( "Mean average: " + total_avg / ( total_marked_written + total_unmarked_written ) / output_size );
    }
}
