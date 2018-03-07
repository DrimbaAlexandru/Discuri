package SignalProcessing.Effects;

import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import AudioDataSource.MemoryADS.SingleBlockADS;
import AudioDataSource.Utils;
import ProjectStatics.ProjectStatics;
import SignalProcessing.Filters.FIR;
import SignalProcessing.Filters.IIR;
import SignalProcessing.Windowing.Windowing;
import Utils.Interval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Created by Alex on 27.02.2018.
 */
public class Repair_in_memory implements IEffect
{
    //TODO: change some of these as configurable parameters in constructor.
    final private int cutoff_frq = 2000;
    final private double preamp = Math.pow( 2, -30.0 / 6 );
    final private double postamp = Math.pow( 2, 30.0 / 6 );
    final private int riaa_length = 2047;
    final private int high_pass_length = 511;
    final private int max_repair_size = 256;
    final private int fetch_ratio = 10;
    //TODO change formula
    final private int cache_size = Math.max( max_repair_size * ( fetch_ratio * 2 + 1 ), Math.max( high_pass_length, riaa_length ) ) + 1024;

    private boolean work_on_position_domain = true;
    private boolean work_on_high_pass = true;

    public void setWork_on_position_domain( boolean work_on_position_domain )
    {
        this.work_on_position_domain = work_on_position_domain;
    }

    public void setWork_on_high_pass( boolean work_on_high_pass )
    {
        this.work_on_high_pass = work_on_high_pass;
    }

    @Override
    public String getName()
    {
        return "Repair in high band of position domain 2";
    }

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        /*
            Local variables.
            Filter definitions
        */
        final double integration_coeffs[] = { 1, -1 };
        final Function< Double, Double > window = Windowing.Blackman_window;

        final FIR derivation_filter = new FIR( new double[]{ 1, -1 }, 2 );
        final FIR riaa_filter = FIR.fromFreqResponse( FIR.get_RIAA_response( riaa_length, dataSource.get_sample_rate() ), riaa_length, dataSource.get_sample_rate(), riaa_length );
        final FIR inverse_riaa_filter = FIR.fromFreqResponse( FIR.get_inverse_RIAA_response( riaa_length, dataSource.get_sample_rate() ), riaa_length, dataSource.get_sample_rate(), riaa_length );
        final FIR high_pass_filter = FIR.fromFreqResponse( FIR.pass_cut_freq_resp( high_pass_length, cutoff_frq, dataSource.get_sample_rate(), -96, 0 ), high_pass_length, dataSource.get_sample_rate(), high_pass_length );
        final IIR integrated_riia_filter = new IIR( inverse_riaa_filter.getFf(), inverse_riaa_filter.getFf_coeff_nr(), integration_coeffs, integration_coeffs.length );
        final IIR integration_filter = new IIR( new double[]{ 1 }, 1, integration_coeffs, integration_coeffs.length );

        Windowing.apply( integrated_riia_filter.getFf(), integrated_riia_filter.getFf_coeff_nr(), ( v ) -> preamp );
        Windowing.apply( riaa_filter.getFf(), riaa_filter.getFf_coeff_nr(), ( v ) -> postamp );

        Windowing.apply( integrated_riia_filter.getFf(), integrated_riia_filter.getFf_coeff_nr(), window );
        Windowing.apply( riaa_filter.getFf(), riaa_filter.getFf_coeff_nr(), window );
        Windowing.apply( high_pass_filter.getFf(), high_pass_filter.getFf_coeff_nr(), window );

        /*
            Data sources
        */
        final SingleBlockADS workADS = new SingleBlockADS( dataSource.get_sample_rate(), dataSource.get_channel_number(), cache_size, new Interval( 0, 0 ) );
        final double[][] work_samples = workADS.getBuffer();

        /*
            Effects
        */
        FIR_Filter fir_filter = new FIR_Filter();
        Equalizer equalizer = new Equalizer();
        IIR_with_centered_FIR iir_with_centered_fir = new IIR_with_centered_FIR();
        Repair repair = new Repair();

        repair.set_fetch_ratio( fetch_ratio );

        /*
            Indexes and other shit
        */
        int i, k;
        int second;
        AudioSamplesWindow win;
        final int side_length = Math.max( ( work_on_high_pass ) ? high_pass_length / 2 : 0, ( work_on_position_domain ) ? Math.max( riaa_length / 2, high_pass_length / 2 ) : 0 );

        Interval required_interval;

        /*
            Work
        */
        if( interval.l < 0 )
        {
            interval.l = 0;
        }
        if( interval.r > dataSource.get_sample_number() )
        {
            interval.r = dataSource.get_sample_number();
        }

        for( k = 0; k < dataSource.get_channel_number(); k++ )
        {
            List< Interval > repair_intervals = new ArrayList<>();
            /*Interval aux_interval;
            aux_interval = ProjectStatics.getMarkerFile().getNextMark( interval.l, k );
            while( aux_interval != null && interval.includes( aux_interval ) )
            {
                repair_intervals.add( aux_interval );
                aux_interval = ProjectStatics.getMarkerFile().getNextMark( aux_interval.r, k );
            }*/
            repair_intervals.add( new Interval( 200, 205, false ) ); // requires [150,255)
            repair_intervals.add( new Interval( 210, 230, false ) ); // requires [110,330)
            repair_intervals.add( new Interval( 1005, 1015, false ) ); // requires [905,1115)
            repair_intervals.add( new Interval( 2000, 2010, false ) ); // requires [1900,2110)
            repair_intervals.add( new Interval( 6400, 6410, false ) ); // requires [1900,2110)
            repair_intervals.add( new Interval( 20000, 20050, false ) ); // requires [19500,20550)

            repair_intervals.sort( ( i1, i2 ) ->
                                   {
                                       return -( i2.l - i2.get_length() * fetch_ratio ) + ( i1.l - i1.get_length() * fetch_ratio );
                                   } );

            repair.setAffected_channels( Arrays.asList( k ) );

            second = -1;

            for( Interval repair_interval : repair_intervals )
            {
                try
                {
                    repair.set_fetch_ratio( fetch_ratio );
                    required_interval = new Interval( repair_interval.l - fetch_ratio * repair_interval.get_length() - side_length, repair_interval.r + fetch_ratio * repair_interval.get_length() + side_length, false );
                    required_interval.limit( 0, dataSource.get_sample_number() );
                    if( required_interval.l < 0 || required_interval.r > dataSource.get_sample_number() )
                    {
                        throw new DataSourceException( "Not enough samples for linear prediction", DataSourceExceptionCause.SAMPLE_NOT_CACHED );
                    }
                    if( repair_interval.get_length() > max_repair_size )
                    {
                        throw new DataSourceException( "Repair interval too big", DataSourceExceptionCause.GENERIC_ERROR );
                    }

                    //Complete the buffer
                    if( !workADS.getInterval().includes( required_interval ) )
                    {
                        int shift_amount;
                        Interval appended_interval;
                        Interval processed_interval = workADS.getInterval();

                        shift_amount = required_interval.l - processed_interval.l;
                        if( shift_amount >= processed_interval.get_length() ) // All data in buffer must be calculated
                        {
                            appended_interval = new Interval( required_interval.l, cache_size );
                            appended_interval.limit( 0, dataSource.get_sample_number() );

                            if( work_on_position_domain )
                            {
                                int src_offset;
                                iir_with_centered_fir.setMax_chunk_size( cache_size + riaa_length );
                                iir_with_centered_fir.setFilter( integrated_riia_filter );

                                SingleBlockADS iriaa_result = new SingleBlockADS( dataSource.get_sample_rate(), dataSource.get_channel_number(), 0, new Interval( 0, 0 ) );
                                iir_with_centered_fir.apply( dataSource, iriaa_result, appended_interval );
                                if( !iriaa_result.getInterval().includes( appended_interval ) )
                                {
                                    throw new DataSourceException( "Assertion error" );
                                }

                                src_offset = appended_interval.l - iriaa_result.getInterval().l;
                                for( int k2 = 0; k2 < dataSource.get_channel_number(); k2++ )
                                {
                                    for( i = 0; i < appended_interval.get_length(); i++ )
                                    {
                                        work_samples[ k ][ i ] = iriaa_result.getBuffer()[ k ][ i + src_offset ];
                                    }
                                }
                                workADS.setInterval( appended_interval );
                            }
                            else
                            {
                                win = dataSource.get_samples( appended_interval.l, appended_interval.get_length() );
                                for( int k2 = 0; k2 < dataSource.get_channel_number(); k2++ )
                                {
                                    for( i = 0; i < win.getInterval().get_length(); i++ )
                                    {
                                        work_samples[ k ][ i ] = win.getSamples()[ k ][ i ];
                                    }
                                }
                                workADS.setInterval( win.getInterval() ); // could be replaced with put_samples call in work_ads ?
                            }
                        }
                        else // we can use previously calculated samples
                        {
                            int k2, offset, src_offset;

                            appended_interval = new Interval( processed_interval.r, cache_size - processed_interval.get_length() + shift_amount );
                            appended_interval.limit( 0, dataSource.get_sample_number() );

                            //Shift the buffer
                            Interval shifted_interval = workADS.getInterval();

                            for( k2 = 0; k2 < dataSource.get_channel_number(); k2++ )
                            {
                                for( i = 0; i < shifted_interval.get_length() - shift_amount; i++ )
                                {
                                    work_samples[ k2 ][ i ] = work_samples[ k2 ][ i + shift_amount ];
                                }
                            }
                            shifted_interval.l += shift_amount;
                            offset = shifted_interval.get_length();
                            //Complete the buffer
                            if( work_on_position_domain )
                            {
                                equalizer.setMax_chunk_size( cache_size + riaa_length );
                                equalizer.setFilter( inverse_riaa_filter );

                                SingleBlockADS iriaa_result = new SingleBlockADS( dataSource.get_sample_rate(), dataSource.get_channel_number(), 0, new Interval( 0, 0 ) );
                                equalizer.apply( dataSource, iriaa_result, appended_interval );
                                if( !iriaa_result.getInterval().includes( appended_interval ) )
                                {
                                    throw new DataSourceException( "Assertion error" );
                                }

                                src_offset = appended_interval.l - iriaa_result.getInterval().l;
                                for( k2 = 0; k2 < dataSource.get_channel_number(); k2++ )
                                {
                                    for( i = 0; i < appended_interval.get_length(); i++ )
                                    {
                                        work_samples[ k ][ i + offset ] = iriaa_result.getBuffer()[ k ][ i + src_offset ];
                                    }
                                }
                                shifted_interval.r += appended_interval.get_length();
                                workADS.setInterval( shifted_interval );

                                iir_with_centered_fir.setMax_chunk_size( cache_size + riaa_length );
                                iir_with_centered_fir.setFilter( integration_filter );
                                iir_with_centered_fir.apply( workADS, workADS, new Interval( processed_interval.r, appended_interval.get_length() ) );
                            }
                            else
                            {
                                win = dataSource.get_samples( appended_interval.l, appended_interval.get_length() );
                                for( k2 = 0; k2 < dataSource.get_channel_number(); k2++ )
                                {
                                    for( i = 0; i < win.getInterval().get_length(); i++ )
                                    {
                                        work_samples[ k ][ i + offset ] = win.getSamples()[ k ][ i ];
                                    }
                                }
                                shifted_interval.r += appended_interval.get_length();
                                workADS.setInterval( shifted_interval );
                            }
                        }
                    }

                    //Prepare the destination buffer for repaired samples
                    SingleBlockADS repair_dest = new SingleBlockADS( dataSource.get_sample_rate(), dataSource.get_channel_number(), 0, new Interval( 0, 0 ) );
                    int repair_dest_side_len = work_on_position_domain ? riaa_length / 2 + 1 : 0;
                    Interval repair_dest_interval = new Interval( repair_interval.l - repair_dest_side_len, repair_interval.r + repair_dest_side_len, false );
                    repair_dest_interval.limit( 0, dataSource.get_sample_number() );
                    repair_dest.put_samples( workADS.get_samples( repair_dest_interval.l, repair_dest_interval.get_length() ) );

                    //Predict the new samples
                    if( work_on_high_pass )
                    {
                        SingleBlockADS high_pass = new SingleBlockADS( dataSource.get_sample_rate(), dataSource.get_channel_number(), 0, new Interval( 0, 0 ) );

                        equalizer.setMax_chunk_size( cache_size + high_pass_length );
                        equalizer.setFilter( high_pass_filter );
                        equalizer.apply( workADS, high_pass, repair_dest_interval );

                        AudioSamplesWindow residue_win = workADS.get_samples( repair_interval.l, repair_interval.get_length() );
                        AudioSamplesWindow high_pass_win = high_pass.get_samples( repair_interval.l, repair_interval.get_length() );
                        for( k = 0; k < dataSource.get_channel_number(); k++ )
                        {
                            for( i = repair_interval.l; i < repair_interval.r; i++ )
                            {
                                residue_win.putSample( i, k, residue_win.getSample( i, k ) - high_pass_win.getSample( i, k ) );
                            }
                        }

                        repair.apply( high_pass, high_pass, repair_interval );
                        high_pass_win = high_pass.get_samples( repair_interval.l, repair_interval.get_length() );

                        for( k = 0; k < dataSource.get_channel_number(); k++ )
                        {
                            for( i = repair_interval.l; i < repair_interval.r; i++ )
                            {
                                residue_win.putSample( i, k, residue_win.getSample( i, k ) + high_pass_win.getSample( i, k ) );
                            }
                        }
                        repair_dest.put_samples( residue_win );
                    }
                    else
                    {
                        repair.apply( workADS, repair_dest, repair_interval );
                    }

                    //Save the repaired samples the the DD
                    if( work_on_position_domain )
                    {
                        fir_filter.setMax_chunk_size( cache_size + derivation_filter.getFf_coeff_nr() );
                        fir_filter.setFilter( derivation_filter );
                        fir_filter.apply( repair_dest, repair_dest, repair_dest_interval );

                        equalizer.setMax_chunk_size( cache_size + riaa_length );
                        equalizer.setFilter( riaa_filter );
                        equalizer.apply( repair_dest, repair_dest, repair_interval );
                    }
                    dataDest.put_samples( repair_dest.get_samples( repair_interval.l, repair_interval.get_length() ) );
                }
                catch( DataSourceException ex )
                {
                    ex.printStackTrace();
                }
                if( repair_interval.l / dataSource.get_sample_rate() > second )
                {
                    second = repair_interval.l / dataSource.get_sample_rate();
                    System.out.println( "Repairing at second " + second + " on channel " + k );
                }
            }
        }
    }
}
