package SignalProcessing.Effects;

import AudioDataSource.ADCache.AudioSamplesWindow;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import AudioDataSource.MemoryADS.SingleBlockADS;
import MarkerFile.Marking;
import ProjectStatics.ProjectStatics;
import SignalProcessing.Filters.FIR;
import SignalProcessing.Filters.IIR;
import SignalProcessing.Windowing.Windowing;
import Utils.Interval;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Created by Alex on 27.02.2018.
 */
public class Repair_in_memory implements IEffect
{
    //TODO: change some of these as configurable parameters in constructor.
    final private int cutoff_frq;
    final private int riaa_length;
    final private int high_pass_length;
    final private int max_repair_size;
    final private int fetch_ratio;
    final private int cache_size;

    private boolean work_on_position_domain = false;
    private boolean work_on_high_pass = false;

    public Repair_in_memory()
    {
        this( 2000, 2047, 511, 512, 16 );
    }

    public Repair_in_memory( int high_pass_frequency, int riaa_filter_length, int band_pass_filter_length, int max_repaired_size, int repair_fetch_ratio )
    {
        cutoff_frq = high_pass_frequency;
        riaa_length = riaa_filter_length;
        high_pass_length = band_pass_filter_length;
        max_repair_size = max_repaired_size;
        fetch_ratio = repair_fetch_ratio;
        cache_size = max_repair_size * ( fetch_ratio * 2 + 1 ) + Math.max( high_pass_length, riaa_length ) + 577;
    }

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
        return "Repair in memory";
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

        //Windowing.apply( inverse_riaa_filter.getFf(), inverse_riaa_filter.getFf_coeff_nr(), ( v ) -> preamp );
        //Windowing.apply( integrated_riia_filter.getFf(), integrated_riia_filter.getFf_coeff_nr(), ( v ) -> preamp );
        //Windowing.apply( riaa_filter.getFf(), riaa_filter.getFf_coeff_nr(), ( v ) -> postamp );

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

        /*
            Indexes and other shit
        */
        int i;
        int second;
        //side length e numarul de sample-uri necesar la dreapta si la stanga selectiei pentru repair, necesare la high-pass si riaa
        final int side_length = Math.max( ( work_on_high_pass ) ? high_pass_length / 2 : 0, ( work_on_position_domain ) ? riaa_length / 2 : 0 );

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


        List< Marking > repair_intervals = ProjectStatics.getMarkerFile().get_all_markings( interval );

        /*repair_intervals.add( new Marking( 200, 204, 0 ) ); // requires [150,255)
        repair_intervals.add( new Marking( 210, 229, 0 ) ); // requires [110,330)
        repair_intervals.add( new Marking( 1005, 1014, 0 ) ); // requires [905,1115)
        repair_intervals.add( new Marking( 2000, 2010, 0 ) ); // requires [1900,2110)
        repair_intervals.add( new Marking( cache_size - 5, cache_size + 4, 0 ) ); // requires [7900,8110)
        repair_intervals.add( new Marking( 20000, 20050, 0 ) ); // requires [19500,20550)
*/
        repair_intervals.sort( ( i1, i2 ) ->
                               {
                                   return -( i2.getInterval().l - i2.getInterval().get_length() * fetch_ratio ) + ( i1.getInterval().l - i1.getInterval().get_length() * fetch_ratio );
                               } );

        second = -1;

        for( Marking marking : repair_intervals )
        {
            Interval repair_interval = marking.getInterval();
            try
            {
                Interval overall_required_interval;
                Interval prediction_required_interval;

                repair.set_fetch_ratio( fetch_ratio );
                repair.setAffected_channels( Arrays.asList( marking.getChannel() ) );
                prediction_required_interval = new Interval( repair_interval.l - fetch_ratio * repair_interval.get_length(), repair_interval.r + fetch_ratio * repair_interval.get_length(), false );
                overall_required_interval = new Interval( prediction_required_interval.l - side_length, prediction_required_interval.r + side_length, false );
                overall_required_interval.limit( 0, dataSource.get_sample_number() );

                if( prediction_required_interval.l < 0 || prediction_required_interval.r > dataSource.get_sample_number() )
                {
                    throw new DataSourceException( "Not enough samples for linear prediction", DataSourceExceptionCause.INTERPOLATION_EXCEPTION );
                }
                if( repair_interval.get_length() > max_repair_size )
                {
                    throw new DataSourceException( "Repair interval too big", DataSourceExceptionCause.WARNING );
                }

                //Complete the buffer
                if( !workADS.getInterval().includes( overall_required_interval ) )
                {
                    int shift_amount;
                    Interval append_interval;
                    Interval cached_interval = workADS.getInterval();

                    shift_amount = overall_required_interval.l - cached_interval.l;
                    if( shift_amount >= cached_interval.get_length() ) // All data in buffer must be calculated
                    {
                        append_interval = new Interval( overall_required_interval.l, cache_size );
                        append_interval.limit( 0, dataSource.get_sample_number() );

                        if( work_on_position_domain )
                        {
                            int src_offset;
                            iir_with_centered_fir.setMax_chunk_size( cache_size + riaa_length );
                            iir_with_centered_fir.setFilter( integrated_riia_filter );

                            SingleBlockADS iriaa_result = new SingleBlockADS( dataSource.get_sample_rate(), dataSource.get_channel_number(), 0, new Interval( 0, 0 ) );
                            iir_with_centered_fir.apply( dataSource, iriaa_result, append_interval );
                            if( !iriaa_result.getInterval().includes( append_interval ) )
                            {
                                throw new DataSourceException( "Assertion error" );
                            }

                            src_offset = append_interval.l - iriaa_result.getInterval().l;
                            for( int k2 = 0; k2 < dataSource.get_channel_number(); k2++ )
                            {
                                for( i = 0; i < append_interval.get_length(); i++ )
                                {
                                    work_samples[ k2 ][ i ] = iriaa_result.getBuffer()[ k2 ][ i + src_offset ];
                                }
                            }
                            workADS.setInterval( append_interval );
                        }
                        else
                        {
                            AudioSamplesWindow win = dataSource.get_samples( append_interval.l, append_interval.get_length() );
                            for( int k2 = 0; k2 < dataSource.get_channel_number(); k2++ )
                            {
                                for( i = 0; i < win.getInterval().get_length(); i++ )
                                {
                                    work_samples[ k2 ][ i ] = win.getSamples()[ k2 ][ i ];
                                }
                            }
                            workADS.setInterval( win.getInterval() );
                        }
                    }
                    else // we can use previously calculated samples
                    {
                        int k2, offset, src_offset;

                        append_interval = new Interval( cached_interval.r, cache_size - cached_interval.get_length() + shift_amount );
                        append_interval.limit( 0, dataSource.get_sample_number() );

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
                            equalizer.apply( dataSource, iriaa_result, append_interval );
                            if( !iriaa_result.getInterval().includes( append_interval ) )
                            {
                                throw new DataSourceException( "Assertion error" );
                            }

                            src_offset = append_interval.l - iriaa_result.getInterval().l;
                            for( k2 = 0; k2 < dataSource.get_channel_number(); k2++ )
                            {
                                for( i = 0; i < append_interval.get_length(); i++ )
                                {
                                    work_samples[ k2 ][ i + offset ] = iriaa_result.getBuffer()[ k2 ][ i + src_offset ];
                                }
                            }
                            shifted_interval.r += append_interval.get_length();
                            workADS.setInterval( shifted_interval );

                            iir_with_centered_fir.setMax_chunk_size( cache_size + riaa_length );
                            iir_with_centered_fir.setFilter( integration_filter );
                            iir_with_centered_fir.apply( workADS, workADS, append_interval );
                        }
                        else
                        {
                            AudioSamplesWindow win = dataSource.get_samples( append_interval.l, append_interval.get_length() );
                            for( k2 = 0; k2 < dataSource.get_channel_number(); k2++ )
                            {
                                for( i = 0; i < win.getInterval().get_length(); i++ )
                                {
                                    work_samples[ k2 ][ i + offset ] = win.getSamples()[ k2 ][ i ];
                                }
                            }
                            shifted_interval.r += append_interval.get_length();
                            workADS.setInterval( shifted_interval );
                        }
                    }
                }

                //Prepare the destination buffer for repaired samples
                SingleBlockADS repair_dest = new SingleBlockADS( dataSource.get_sample_rate(), dataSource.get_channel_number(), 0, new Interval( 0, 0 ) );
                int repair_dest_side_len = work_on_position_domain ? riaa_length / 2 : 0;
                Interval repair_dest_interval = new Interval( repair_interval.l - repair_dest_side_len, repair_interval.r + repair_dest_side_len, false );
                Interval repair_dest_fetch = new Interval( repair_dest_interval.l, repair_dest_interval.get_length() );
                if( work_on_position_domain )
                {
                    repair_dest_fetch.l -= 1;
                }
                repair_dest_fetch.limit( 0, dataSource.get_sample_number() );
                repair_dest_interval.limit( 0, dataSource.get_sample_number() );
                repair_dest.put_samples( workADS.get_samples( repair_dest_fetch.l, repair_dest_fetch.get_length() ) );

                //Predict the new samples
                if( work_on_high_pass )
                {
                    SingleBlockADS high_pass = new SingleBlockADS( dataSource.get_sample_rate(), dataSource.get_channel_number(), 0, new Interval( 0, 0 ) );

                    equalizer.setMax_chunk_size( cache_size + high_pass_length );
                    equalizer.setFilter( high_pass_filter );
                    equalizer.apply( workADS, high_pass, prediction_required_interval );

                    AudioSamplesWindow residue_win = workADS.get_samples( repair_interval.l, repair_interval.get_length() );
                    AudioSamplesWindow high_pass_win = high_pass.get_samples( repair_interval.l, repair_interval.get_length() );
                    for( int k2 = 0; k2 < dataSource.get_channel_number(); k2++ )
                    {
                        for( i = repair_interval.l; i < repair_interval.r; i++ )
                        {
                            residue_win.putSample( i, k2, residue_win.getSample( i, k2 ) - high_pass_win.getSample( i, k2 ) );
                        }
                    }

                    repair.apply( high_pass, high_pass, repair_interval );
                    high_pass_win = high_pass.get_samples( repair_interval.l, repair_interval.get_length() );

                    for( int k2 = 0; k2 < dataSource.get_channel_number(); k2++ )
                    {
                        for( i = repair_interval.l; i < repair_interval.r; i++ )
                        {
                            residue_win.putSample( i, k2, residue_win.getSample( i, k2 ) + high_pass_win.getSample( i, k2 ) );
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
                AudioSamplesWindow win_rep = repair_dest.get_samples( repair_interval.l, repair_interval.get_length() );
                AudioSamplesWindow win_dst = dataDest.get_samples( repair_interval.l, repair_interval.get_length() );
                win_dst.getSamples()[ marking.getChannel() ] = win_rep.getSamples()[ marking.getChannel() ];
                dataDest.put_samples( win_dst );
            }
            catch( DataSourceException ex )
            {
                switch( ex.getDSEcause() )
                {
                    case INTERPOLATION_EXCEPTION:
                    case WARNING:
                        ex.printStackTrace();
                        break;
                    default:
                        throw ex;
                }
            }
            if( repair_interval.l / dataSource.get_sample_rate() > second )
            {
                second = repair_interval.l / dataSource.get_sample_rate();
                System.out.println( "Repairing at second " + second );
            }
        }
    }
}
