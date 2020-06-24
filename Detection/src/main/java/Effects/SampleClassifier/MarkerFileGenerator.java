package Effects.SampleClassifier;

import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.IAudioDataSource;
import ProjectManager.ProjectManager;
import Utils.DataTypes.EffectType;
import Utils.DataTypes.Interval;
import Utils.Exceptions.DataSourceException;
import Utils.Exceptions.DataSourceExceptionCause;
import Utils.DataStructures.MarkerFile.*;
import Effects.IEffect;

import java.io.FileWriter;
import java.io.IOException;

import static java.lang.Math.sqrt;
import static net.jafama.FastMath.pow;

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
    float damage_amplif = 1.0f;

    private boolean gen_marker = true;
    private boolean gen_mvg_avg = false;

    private float progress = 0.0f;

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
//        int longest_marking = -1;
        long sum_length = 0;
        int cnt = 1;
        int i, k;
        boolean mark;
        double moving_sum[] = new double[ dataSource.get_channel_number() ];
        float moving_avg[][] = new float[ dataSource.get_channel_number() ][ 1 ];
        float sample;
        final int moving_avg_half = moving_avg_size / 2;

        MarkerFile mf = ProjectManager.getMarkerFile();
        AudioSamplesWindow win = null;

        /* Check sound file */
        if( dataSource.get_channel_number() > 2 )
        {
            throw new DataSourceException( "Not mono or stereo" );
        }

        /* Initialize variables */
        Marking[] last_marking = new Marking[ dataSource.get_channel_number() ];
        Marking[] current_marking = new Marking[ dataSource.get_channel_number() ];

        progress = 0.0f;
        interval.limit( moving_avg_half, dataSource.get_sample_number() - moving_avg_half );
        moving_avg_size = moving_avg_half * 2 + 1;

        if( gen_marker )
        {
            for( k = 0; k < dataSource.get_channel_number(); k++ )
            {
                last_marking[ k ] = new Marking( -1, -1, k );
                current_marking[ k ] = new Marking( -1, -1, k );
            }
        }

        /* Generate the markings file */
        for( i = interval.l; i < interval.r; i++ )
        {
            if( ( i - interval.l ) % dataSource.get_sample_rate() == 0 )
            {
                win = dataSource.get_samples( i - moving_avg_half, dataSource.get_sample_rate() + moving_avg_half * 2 );
                System.out.println( "Processed " + i / dataSource.get_sample_rate() + " seconds" );
                progress = 1.0f * i / interval.get_length();
            }

            for( k = 0; k < dataSource.get_channel_number(); k++ )
            {
                /* Initialize the moving average */
                if( i == interval.l )
                {
                    moving_sum[ k ] = 0.0;
                    for( int j = -moving_avg_half; j < moving_avg_half; j++ )
                    {
                        sample = Math.abs( win.getSample( i + j, k ) );
                        if( sample >= abs_threshold )
                        {
                            moving_sum[ k ] += sample;
                        }
                    }
                }

                /* Add the next value to the moving average */
                sample = Math.abs( win.getSample( i + moving_avg_half, k ) );
                if( sample >= abs_threshold )
                {
                    moving_sum[ k ] += sample;
                }

                mark = ( Math.abs( win.getSample( i, k ) ) >= abs_threshold
                      && Math.abs( win.getSample( i, k ) ) >= ( moving_sum[ k ] / moving_avg_size ) * spike_threshold );

                if( gen_marker )
                {
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
                            if( duplicate_L_to_R && k == 0 )
                            {
                                mf.addMark( current_marking[ k ].get_first_marked_sample(), current_marking[ k ].get_last_marked_sample(), 1 );
                            }

                            // if( mf.getNextMark( i - 1, k ).get_length() > longest_marking )
                            // {
                            //     longest_marking = mf.getNextMark( i - 1, k ).get_length();
                            // }

                            current_marking[ k ].set_first_marked_sample( -1 );
                            current_marking[ k ].set_last_marked_sample( -1 );
                        }
                    }
                }
                moving_avg[ k ][ 0 ] = ( float )( moving_sum[ k ] / moving_avg_size );

                if( moving_avg[ k ][ 0 ] >= 0.005f )
                {
                    moving_avg[ k ][ 0 ] -= 0.005f;
                    moving_avg[ k ][ 0 ] *= damage_amplif;
                    moving_avg[ k ][ 0 ] += 0.005f;
                    if( moving_avg[ k ][ 0 ] > 1.0f )
                    {
                        moving_avg[ k ][ 0 ] = 1.0f;
                    }
                }

                // moving_avg[ k ][ 0 ] = ( float )pow( moving_avg[ k ][ 0 ], 0.75 );

                /* Subtract the first sample of the moving average, which will no longer be needed in the next step */
                sample = Math.abs( win.getSample( i - moving_avg_half, k ) );
                if( sample >= abs_threshold )
                {
                    moving_sum[ k ] -= sample;
                }
            }
            if( dataDest != null && gen_mvg_avg )
            {
                dataDest.put_samples( new AudioSamplesWindow( moving_avg, i, 1, dataSource.get_channel_number() ) );
            }
        }

        if( gen_marker && dest_path != null )
        {
            try
            {
                mf.writeMarkingsToFile( new FileWriter( dest_path + " s " + side_extend + " m " + min_marking_spacing + " " + String.format( "%.4f", spike_threshold ) + " avg " + ( sum_length / cnt ) + ".txt" ) );
                System.out.println( "Average: " + sum_length / cnt );
                System.out.println( "Count: " + cnt );
            }
            catch( IOException e )
            {
                throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.IO_ERROR );
            }
        }
    }

    @Override
    public float getProgress()
    {
        return progress;
    }

    @Override
    public EffectType getEffectType()
    {
        return EffectType.DAMAGE_READ_EFFECT;
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

    public void setGen_marker( boolean gen_marker )
    {
        this.gen_marker = gen_marker;
    }

    public void setGen_mvg_avg( boolean gen_mvg_avg )
    {
        this.gen_mvg_avg = gen_mvg_avg;
    }

    public void setDamage_amplif( float damage_amplif )
    {
        this.damage_amplif = damage_amplif;
    }
}