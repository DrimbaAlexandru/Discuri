package SignalProcessing.Effects;

import AudioDataSource.AudioSamplesWindow;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import AudioDataSource.MemoryADS.SingleBlockADS;
import MarkerFile.Marking;
import ProjectManager.ProjectManager;
import SignalProcessing.Filters.FIR;
import SignalProcessing.Filters.IIR;
import Utils.Interval;
import Utils.MyPair;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Alex on 27.02.2018.
 */
public class Repair_in_memory implements IEffect
{
    //TODO: change some of these as configurable parameters in constructor.
    private int low_cutoff_frq;
    private int high_cutoff_frq;
    final private int riaa_length;
    final private int high_pass_length;
    final private int max_repair_size;
    final private int fetch_ratio;
    final private int cache_size;

    private boolean work_on_position_domain = false;
    private boolean work_on_band_pass = false;

    private float progress = 0;

    public Repair_in_memory()
    {
        this( 2000, -1, 1023, 511, 512, 16 );
    }

    public Repair_in_memory( int band_pass_low_bound, int band_pass_high_bound, int riaa_filter_length, int band_pass_filter_length, int max_repaired_size, int repair_fetch_ratio )
    {
        low_cutoff_frq = band_pass_low_bound;
        high_cutoff_frq = band_pass_high_bound;
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

    public void setWork_on_band_pass( boolean work_on_band_pass )
    {
        this.work_on_band_pass = work_on_band_pass;
    }

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        progress = 0;
        /*
            Local variables.
            Filter definitions
        */
        final float integration_coeffs[] = { 1, -1 };
        final MyPair< float[], float[] > riaa_resp = FIR.get_RIAA_response();
        final MyPair< float[], float[] > inv_riaa_resp = FIR.get_RIAA_response();

        final int nr_freqs = dataSource.get_sample_rate() / 2 / 50;
        final float[] freqs = new float[ nr_freqs ];
        final float[] hp_response = new float[ nr_freqs ];
        for( int i = 0; i < nr_freqs; i++ )
        {
            freqs[ i ] = 1.0f * i * dataSource.get_sample_rate() / 2 / nr_freqs;
        }

        if( low_cutoff_frq != -1 )
        {
            FIR.add_pass_cut_freq_resp( freqs, hp_response, nr_freqs, low_cutoff_frq, -96, 0 );
        }
        if( high_cutoff_frq != -1 )
        {
            FIR.add_pass_cut_freq_resp( freqs, hp_response, nr_freqs, high_cutoff_frq, 0, -96 );
        }

        final FIR derivation_filter = new FIR( new float[]{ 1, -1 }, 2 );
        final FIR riaa_filter = FIR.fromFreqResponse( riaa_resp.getLeft(), riaa_resp.getRight(), riaa_resp.getLeft().length, dataSource.get_sample_rate(), riaa_length );
        final FIR inverse_riaa_filter = FIR.fromFreqResponse( inv_riaa_resp.getLeft(), inv_riaa_resp.getRight(), inv_riaa_resp.getLeft().length, dataSource.get_sample_rate(), riaa_length );
        final FIR high_pass_filter = FIR.fromFreqResponse( freqs, hp_response, nr_freqs, dataSource.get_sample_rate(), high_pass_length );

        final IIR integrated_riia_filter = new IIR( inverse_riaa_filter.getFf(), inverse_riaa_filter.getFf_coeff_nr(), integration_coeffs, integration_coeffs.length );
        final IIR integration_filter = new IIR( new float[]{ 1 }, 1, integration_coeffs, integration_coeffs.length );

        /*
            Data sources
        */
        final SingleBlockADS workADS = new SingleBlockADS( dataSource.get_sample_rate(), dataSource.get_channel_number(), cache_size, new Interval( 0, 0 ) );
        final float[][] work_samples = workADS.getBuffer();

        /*
            Effects
        */
        FIR_Filter fir_filter = new FIR_Filter();
        FIR_Equalizer equalizer = new FIR_Equalizer();
        IIR_with_centered_FIR iir_with_centered_fir = new IIR_with_centered_FIR();
        Repair_One repairOne = new Repair_One();

        /*
            Indexes and other shit
        */
        int i;
        int second;
        int marking_index = 0;
        //side length e numarul de sample-uri necesar la dreapta si la stanga selectiei pentru repair, necesare la high-pass si riaa
        final int side_length = Math.max( ( work_on_band_pass ) ? high_pass_length / 2 : 0, ( work_on_position_domain ) ? riaa_length / 2 : 0 );

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


        List< Marking > repair_intervals = ProjectManager.getMarkerFile().get_all_markings( interval );

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

                repairOne.set_fetch_ratio( fetch_ratio );
                repairOne.setAffected_channels( Arrays.asList( marking.getChannel() ) );
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
                if( work_on_band_pass )
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

                    repairOne.apply( high_pass, high_pass, repair_interval );
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
                    repairOne.apply( workADS, repair_dest, repair_interval );
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
            marking_index++;
            progress = 1.0f * marking_index / repair_intervals.size();
            if( repair_interval.l / dataSource.get_sample_rate() > second )
            {
                second = repair_interval.l / dataSource.get_sample_rate();
                System.out.println( "Repairing at second " + second );
            }
        }
    }

    @Override
    public float getProgress()
    {
        return progress;
    }

    public void setBandpass_cutoff_frqs( int low_cutoff_frq, int high_cutoff_frq )
    {
        this.low_cutoff_frq = low_cutoff_frq;
        this.high_cutoff_frq = high_cutoff_frq;
    }
}
