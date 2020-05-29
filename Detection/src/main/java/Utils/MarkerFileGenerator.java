package Utils;

import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.IAudioDataSource;
import Exceptions.DataSourceException;
import AudioDataSource.FileADS.WAVFileAudioSource;
import Exceptions.DataSourceExceptionCause;
import MarkerFile.*;
import SignalProcessing.Effects.IEffect;
import Utils.Interval;

import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by Alex on 16.02.2018.
 */
public class MarkerFileGenerator implements IEffect
{
    private String dest_path;
    boolean duplicate_L_to_R = true;
    float spike_threshold = 0.4f;
    float abs_threshold = 0.015f;
    int side_extend = 6;
    int min_marking_spacing = 4;
    int moving_avg_size = 128;

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        int longest_marking = -1;
        long sum_length = 0;
        int cnt = 0;
        int i, k;
        boolean mark;
        double moving_sum[] = new double[ dataSource.get_channel_number() ];
        float moving_avg[][] = new float[ dataSource.get_channel_number() ][ 1 ];

        MarkerFile mf = new MarkerFile();
        AudioSamplesWindow win = null;

        /* Check sound file */
        if( dataSource.get_channel_number() > 2 )
        {
            throw new DataSourceException( "Not mono or stereo" );
        }

        /* Initialize variables */
        Marking[] last_marking = new Marking[ dataSource.get_channel_number() ];
        Marking[] current_marking = new Marking[ dataSource.get_channel_number() ];

        for( k = 0; k < dataSource.get_channel_number(); k++ )
        {
            last_marking[ k ] = new Marking( -2 * min_marking_spacing, -2 * min_marking_spacing, k );
            current_marking[ k ] = new Marking( -2 * min_marking_spacing, -2 * min_marking_spacing, k );
        }

        /* Generate the markings file */
        for( i = 0; i < dataSource.get_sample_number(); i++ )
        {
            if( i % dataSource.get_sample_rate() == 0 )
            {
                win = dataSource.get_samples( i, dataSource.get_sample_rate() );
                System.out.println( "Processed " + i / dataSource.get_sample_rate() + " seconds" );

                /* Initialize the moving average with data from the beginning of the window */
                for( k = 0; k < dataSource.get_channel_number(); k++ )
                {
                    moving_sum[ k ] = 0.0;
                    for( int j = 0; j < moving_avg_size; j++ )
                    {
                        moving_sum[ k ] += Math.sqrt( Math.abs( win.getSample( i + j, k ) ) );
                    }
                }
            }


            for( k = 0; k < dataSource.get_channel_number(); k++ )
            {
                /* If not at the beginning or the end of the window, update the moving average */
                if( ( i - win.get_first_sample_index() >= moving_avg_size / 2 ) && ( win.get_after_last_sample_index() - 1 - i >= moving_avg_size / 2 ) )
                {
                    moving_sum[ k ] -= Math.sqrt( Math.abs( win.getSample( i - moving_avg_size / 2, k ) ) );
                    moving_sum[ k ] += Math.sqrt( Math.abs( win.getSample( i + moving_avg_size / 2, k ) ) );
                }

                mark = ( Math.abs( win.getSample( i, k ) ) >= abs_threshold && Math.abs( win.getSample( i, k ) ) >= ( moving_sum[ k ] / moving_avg_size ) * spike_threshold );
                if( mark )
                {
                    /* If the value is greater than the threshold, and this isn't a continuation of a previous mark, start a new marking */
                    if( current_marking[ k ].get_first_marked_sample() == -1 )
                    {
                        current_marking[ k ].set_first_marked_sample( i );
                    }
                }
                else
                {
                    /* If the value is smaller than the threshold and the previous mark has just ended, check its length and save it */
                    if( current_marking[ k ].get_first_marked_sample() != -1 )
                    {
                        /* Extend the marking by the constant */
                        current_marking[ k ].set_first_marked_sample( current_marking[ k ].get_first_marked_sample() - side_extend );
                        current_marking[ k ].set_last_marked_sample( i - 1 + side_extend );

                        /* If the non marked space between the current and the previous marking is not large enough, concatenate them */
                        if( current_marking[ k ].get_first_marked_sample() - last_marking[ k ].get_last_marked_sample() < min_marking_spacing )
                        {
                            current_marking[ k ].set_first_marked_sample( last_marking[ k ].get_last_marked_sample() + 1 );
                        }
                        else
                        {
                            cnt++;
                        }

                        last_marking[ k ].set_first_marked_sample( current_marking[ k ].get_first_marked_sample() );
                        last_marking[ k ].set_last_marked_sample( current_marking[ k ].get_last_marked_sample() );

                        sum_length += current_marking[ k ].get_number_of_marked_samples();

                        /* Add the new marking. Add it to channel 1 too if the marking file acts as thresholds for both stereo channels */
                        mf.addMark( current_marking[ k ].get_first_marked_sample(), current_marking[ k ].get_last_marked_sample(), k );
                        if( mf.getNextMark( i - 1, k ).get_length() > longest_marking )
                        {
                            longest_marking = mf.getNextMark( i - 1, k ).get_length();
                        }

                        if( duplicate_L_to_R && k == 0 )
                        {
                            mf.addMark( current_marking[ k ].get_first_marked_sample(), current_marking[ k ].get_last_marked_sample(), 1 );
                        }

                        current_marking[ k ].set_first_marked_sample( -1 );
                        current_marking[ k ].set_last_marked_sample( -1 );
                    }
                }
                moving_avg[ k ][ 0 ] = ( float )( moving_sum[ k ] / moving_avg_size );
            }
            if( dataDest != null )
            {
                dataDest.put_samples( new AudioSamplesWindow( moving_avg, i, 1, dataSource.get_channel_number() ) );
            }

        }

        try
        {

            mf.writeMarkingsToFile( new FileWriter( dest_path + " s " + side_extend + " m " + min_marking_spacing + " " + String.format( "%.4f", spike_threshold ) + " avg " + ( sum_length / cnt ) + ".txt" ) );
            System.out.println( "Longest: " + longest_marking );
            System.out.println( "Average: " + sum_length / cnt );
            System.out.println( "Count: " + cnt );
        }
        catch( IOException e )
        {
            throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.IO_ERROR );
        }
    }

    @Override
    public float getProgress()
    {
        return 0;
    }

    public void setAbs_threshold( float abs_threshold )
    {
        this.abs_threshold = abs_threshold;
    }

    public void setDest_path( String dest_path )
    {
        this.dest_path = dest_path;
    }

    public void setDuplicate_L_to_R( boolean duplicate_L_to_R )
    {
        this.duplicate_L_to_R = duplicate_L_to_R;
    }

    public void setMin_marking_spacing( int min_marking_spacing )
    {
        this.min_marking_spacing = min_marking_spacing;
    }

    public void setMoving_avg_size( int moving_avg_size )
    {
        this.moving_avg_size = moving_avg_size;
    }

    public void setSide_extend( int side_extend )
    {
        this.side_extend = side_extend;
    }

    public void setSpike_threshold( float spike_threshold )
    {
        this.spike_threshold = spike_threshold;
    }
}