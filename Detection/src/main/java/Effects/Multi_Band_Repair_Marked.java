package Effects;

import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.IAudioDataSource;
import AudioDataSource.MemoryADS.SingleWindowMemoryADS;
import Utils.DataTypes.EffectType;
import Utils.Exceptions.DataSourceException;
import Utils.Exceptions.DataSourceExceptionCause;
import Utils.DataStructures.MarkerFile.Marking;
import ProjectManager.ProjectStatics;
import ProjectManager.ProjectManager;
import SignalProcessing.Filters.FIR;
import Utils.DataTypes.Interval;
import Utils.Util_Stuff;

import java.util.*;

/**
 * Created by Alex on 15.06.2018.
 */
public class Multi_Band_Repair_Marked implements IEffect
{
    final private int band_pass_filter_length;
    private ArrayList< Integer > band_cutoffs = new ArrayList<>();
    private int max_repair_size;
    private int fetch_ratio;
    private int buffer_size;
    private boolean repair_residue = false;
    private float peak_threshold = 4;
    private boolean compare_with_direct_repair = false;
    private float progress = 0;

    public Multi_Band_Repair_Marked()
    {
        this( 511, 256 + 128, 6 );
    }

    public void setMax_repair_size( int max_repair_size )
    {
        this.max_repair_size = max_repair_size;
    }

    public Multi_Band_Repair_Marked( int band_pass_filter_length, int max_repaired_size, int repair_fetch_ratio )
    {
        this.band_pass_filter_length = band_pass_filter_length;
        max_repair_size = max_repaired_size;
        fetch_ratio = repair_fetch_ratio;
    }

    private FIR[] get_FIR_filters( int nyquist_frequency ) throws DataSourceException
    {
        final int FREQ_BAND_HZ = 50;
        final int nr_freqs = nyquist_frequency / FREQ_BAND_HZ + 1;

        final int nr_of_bands = band_cutoffs.size();
        final FIR[] filters = new FIR[ nr_of_bands ];
        final float[] freqs = new float[ nr_freqs ];
        final float[] bp_response = new float[ nr_freqs ]; //this is in dB
        final float[] prev_response = new float[ nr_freqs ]; //this is linear

        band_cutoffs.sort( Comparator.reverseOrder() );

        Arrays.fill( prev_response, 0.0f );

        /* Initialize the frequency points list with 50Hz spaced frequencies */
        for( int i = 0; i < nr_freqs; i++ )
        {
            freqs[ i ] = 1.0f * i * FREQ_BAND_HZ;
        }

        for( int f = 0; f < nr_of_bands; f++ )
        {
            /* Create the current bandpass response by adding a high-pass over the previous filter */
            Arrays.fill( bp_response, 0.0f );
            FIR.add_pass_cut_freq_resp( freqs, bp_response, nr_freqs, band_cutoffs.get( f ), -96, 0 );

            Util_Stuff.dB2lin( bp_response, nr_freqs );
            for( int i = 0; i < nr_freqs; i++ )
            {
                float aux = bp_response[ i ];
                bp_response[ i ] -= prev_response[ i ];
                prev_response[ i ] = aux;
            }
            Util_Stuff.lin2dB( bp_response, nr_freqs );

            filters[ f ] = FIR.fromFreqResponse( freqs, bp_response, nr_freqs, nyquist_frequency * 2, band_pass_filter_length );
            //Util_Stuff.plot_in_matlab( filters[ f ].getFf(), filters[ f ].getFf_coeff_nr() );
        }

        return filters;
    }

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        if( dataSource == dataDest )
        {
            throw new DataSourceException( "Data Source and Data Dest cannot be the same", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        progress = 0.0f;
        interval.limit( 0, dataSource.get_sample_number() );

        /**************************************
        Local variables.
        Filter definitions
        ****************************************/
        final int nr_of_bands = band_cutoffs.size();
        FIR[] filters = get_FIR_filters( dataSource.get_sample_rate() / 2 );

        /*
        Data sources and audio sample windows
        */
        final SingleWindowMemoryADS repair_dest;
        final SingleWindowMemoryADS freq_band_ADS[] = new SingleWindowMemoryADS[ nr_of_bands + 1 ]; // freq_band_ADS[nr_of_bands] - buffer-ul pentru reziduu
        AudioSamplesWindow repair_dest_asw, repaired_band_asw;
        float[] src_array, dst_array;

        /*
        * Effects
        */
        final FFT_Equalizer fft_equalizer = new FFT_Equalizer();
        final Repair_One repairOne = new Repair_One();
        repairOne.set_fetch_ratio( fetch_ratio );

        /*
        Indexes and other shit
        */
        int i;
        int marking_index = 0;
        boolean repair_direct;
        Random random = new Random();

        List< Marking > orig_repair_intervals = ProjectManager.getMarkerFile().get_all_markings( interval );
        List< Marking > repair_intervals = new ArrayList<>();

        /* Return early if there's nothing to process */
        if( orig_repair_intervals.size() == 0 || ( !repair_residue && nr_of_bands == 0 && !compare_with_direct_repair ) )
        {
            return;
        }

        /*
         * Prepare the initial state of the variables
         * Allocate the data buffers
         */
        buffer_size = Math.max( ProjectStatics.getProject_cache_size() / 2, max_repair_size * ( fetch_ratio * 2 + 1 ) );
        repair_dest = new SingleWindowMemoryADS( max_repair_size, dataSource.get_channel_number(), dataSource.get_sample_rate() );

        for( i = 0; i < nr_of_bands + 1; i++ )
        {
            freq_band_ADS[ i ] = new SingleWindowMemoryADS( buffer_size, dataSource.get_channel_number(), dataSource.get_sample_rate() );
        }

        /* Split the markings that are longer than the maximum repair size */
        for( Marking m : orig_repair_intervals )
        {
            if( m.get_number_of_marked_samples() > max_repair_size )
            {
                int remaining_length = m.get_number_of_marked_samples();
                while( remaining_length > 0 )
                {
                    int new_len = ( remaining_length < max_repair_size ) ? remaining_length : ( ( int )( max_repair_size * ( 0.75f + ( random.nextFloat() * 2 - 1 ) * 0.15 ) ) ); /* Use a random length between 0.6-0.9*max_repair_size */
                    Marking new_m = new Marking( m.get_last_marked_sample() + 1 - remaining_length, m.get_last_marked_sample() - remaining_length + new_len, m.getChannel() );
                    remaining_length -= new_len;
                    repair_intervals.add( new_m );
                }
            }
            else
            {
                repair_intervals.add( m );
            }
        }

        /* Sort the markings by the first sample needed for repair */
        repair_intervals.sort( ( i1, i2 ) ->
                               {
                                   return -( i2.getInterval().l - i2.getInterval().get_length() * fetch_ratio ) + ( i1.getInterval().l - i1.getInterval().get_length() * fetch_ratio );
                               } );

        /* Limit the interval to the first and last sample needed by the repair process */
        interval.l = repair_intervals.get( 0 ).get_first_marked_sample() - repair_intervals.get( 0 ).getInterval().get_length() * fetch_ratio;
        interval.r = 0;
        for( Marking m:repair_intervals)
        {
            interval.r = Math.max( interval.r, m.get_last_marked_sample() + 1 + m.get_number_of_marked_samples() * fetch_ratio );
        }
        interval.limit( 0, dataSource.get_sample_number() );

        /* Repair each marking individually */
        for( Marking marking : repair_intervals )
        {
            Interval repair_interval = marking.getInterval();
            Interval interval_required_for_prediction = new Interval( repair_interval.l - fetch_ratio * repair_interval.get_length(), repair_interval.r + fetch_ratio * repair_interval.get_length(), false );
            repairOne.setAffected_channels( Arrays.asList( marking.getChannel() ) );

            if( interval_required_for_prediction.l < 0 || interval_required_for_prediction.r > dataSource.get_sample_number() )
            {
                System.err.println( "Not enough samples for linear prediction at marking " + repair_interval );
                continue;
            }

            if( repair_interval.get_length() > max_repair_size )
            {
                throw new DataSourceException( "Repair interval too big", DataSourceExceptionCause.THIS_SHOULD_NEVER_HAPPEN );
            }

            /* Check if this marking shall be repaired_band_asw without splitting into multiple frequency bands */
            repair_direct = repair_residue && ( nr_of_bands == 0 );
            if( !repair_direct && compare_with_direct_repair )
            {
                int rep_len = repair_interval.get_length();
                int spike_det_left_len = Math.min( band_pass_filter_length / 2, repair_interval.l );
                int spike_det_right_len = Math.min( band_pass_filter_length / 2, dataSource.get_sample_number() - repair_interval.r );
                AudioSamplesWindow from_source = dataSource.get_samples( repair_interval.l - spike_det_left_len, rep_len + spike_det_left_len + spike_det_right_len );
                float spike_ratio = get_freq_spike( from_source.getSamples()[ marking.getChannel() ], 0, spike_det_left_len, rep_len, spike_det_right_len );
                repair_direct = ( spike_ratio > peak_threshold );
                if( repair_direct )
                {
                    System.out.println( "Direct repair at on interval " + marking + " with ratio = " + spike_ratio );
                }
            }

            /* If marking is not supposed to be repaired, continue */
            if( !repair_direct && !repair_residue && ( nr_of_bands == 0 ) )
            {
                continue;
            }

            if( !repair_direct )
            {
                /* Repair with splitting the signal into multiple frequency bands */

                // Make sure the workADSs contain enough data to complete the current repair
                if( !freq_band_ADS[ 0 ].get_buffer_interval().includes( interval_required_for_prediction ) )
                {
                    // Shift left as much as possible and as rare as possible. Keep only the data that can be still used
                    int amount_to_shift = interval_required_for_prediction.l - freq_band_ADS[ 0 ].get_buffer_interval().l;
                    for( i = 0; i <= nr_of_bands; i++ )
                    {
                        freq_band_ADS[ i ].shift_interval( amount_to_shift );
                    }

                    /* Compute the next fetchable chunk. Fill the buffer to its max if we need that much data. If not, fill just as needed */
                    Interval missing_region = new Interval( freq_band_ADS[ 0 ].get_buffer_interval().r, buffer_size - freq_band_ADS[ 0 ].get_buffer_interval().get_length(), true );
                    missing_region.limit( interval_required_for_prediction.l, interval.r );

                    for( i = 0; i < nr_of_bands; i++ )
                    {
                        fft_equalizer.setFilter( filters[ i ] );
                        fft_equalizer.setFFT_length( Util_Stuff.next_power_of_two( filters[ i ].getFf_coeff_nr() ) * 2 );
                        fft_equalizer.apply( dataSource, freq_band_ADS[ i ], missing_region );
                    }

                    /* Compute the residue signal by subtracting each filtered signal from the original signal */
                    AudioSamplesWindow win_src = dataSource.get_samples( missing_region.l, missing_region.get_length() );
                    for( i = 0; i < nr_of_bands; i++ )
                    {
                        AudioSamplesWindow win_band = freq_band_ADS[ i ].get_samples( missing_region.l, missing_region.get_length() );
                        for( int k = 0; k < win_src.get_channel_number(); k++ )
                        {
                            dst_array = win_src.getSamples()[ k ];
                            src_array = win_band.getSamples()[ k ];
                            for( int j = 0; j < win_src.get_length(); j++ )
                            {
                                dst_array[ j ] -= src_array[ j ];
                            }
                        }
                    }
                    freq_band_ADS[ nr_of_bands ].put_samples( win_src );

                    /*for( i = 0; i <= nr_of_bands; i++ )
                    {
                        win = freq_band_ADS[ i ].get_samples( freq_band_ADS[ i ].get_buffer_interval().l, freq_band_ADS[ i ].get_buffer_interval().get_length() );
                        Util_Stuff.plot_in_matlab( win.getSamples()[ 0 ], win.get_length() );
                        System.out.println( "----" );
                    }*/
                }

                /*
                * Begin the repair process
                * Start with the residual signal, and then add each repaired_band_asw frequency band.
                 */
                repair_dest.reset_interval( repair_interval );

                if( repair_residue )
                {
                    repairOne.apply( freq_band_ADS[ nr_of_bands ], repair_dest, repair_interval );
                    repair_dest_asw = repair_dest.get_samples( repair_interval.l, repair_interval.get_length() );
                }
                else
                {
                    repair_dest_asw = freq_band_ADS[ nr_of_bands ].get_samples( repair_interval.l, repair_interval.get_length() );
                }
                //Util_Stuff.plot_in_matlab( repaired_samples, repair_interval.get_length() );
                //System.out.println( "----" );

                dst_array = repair_dest_asw.getSamples()[ marking.getChannel() ];
                for( i = 0; i < nr_of_bands; i++ )
                {
                    repairOne.apply( freq_band_ADS[ i ], repair_dest, repair_interval );
                    repaired_band_asw = repair_dest.get_samples( repair_interval.l, repair_interval.get_length() );
                    src_array = repaired_band_asw.getSamples()[ marking.getChannel() ];

                    for( int j = 0; j < repair_interval.get_length(); j++ )
                    {
                        dst_array[ j ] += src_array[ j ];
                    }
                    //Util_Stuff.plot_in_matlab( repaired_samples, repair_interval.get_length() );
                    //System.out.println( "----" );
                }
                repaired_band_asw = dataDest.get_samples( repair_interval.l, repair_interval.get_length() );
                repaired_band_asw.getSamples()[ marking.getChannel() ] = repair_dest_asw.getSamples()[ marking.getChannel() ];
                dataDest.put_samples( repaired_band_asw );
            }
            else
            {
                repair_dest.reset_interval( repair_interval );
                repaired_band_asw = dataDest.get_samples( repair_interval.l, repair_interval.get_length() );
                repairOne.apply( dataSource, repair_dest, repair_interval );
                AudioSamplesWindow direct_repaired = repair_dest.get_samples( repair_interval.l, repair_interval.get_length() );
                repaired_band_asw.getSamples()[ marking.getChannel() ] = direct_repaired.getSamples()[ marking.getChannel() ];
                dataDest.put_samples( repaired_band_asw );
            }

            marking_index++;
            progress = 1.0f * marking_index / repair_intervals.size();
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
        return EffectType.NORMAL_AUDIO_EFFECT;
    }

    public ArrayList< Integer > getBand_cutoffs()
    {
        return band_cutoffs;
    }

    public void setRepair_residue( boolean repair_residue )
    {
        this.repair_residue = repair_residue;
    }

    private float get_freq_spike( float[] samples, int start_offset, int left_len, int mid_len, int right_len )
    {
        float left_ampl = 0, mid_ampl = 0, right_ampl = 0;
        for( int i = start_offset; i < start_offset + left_len; i++ )
        {
            left_ampl = Math.max( left_ampl, Math.abs( samples[ i ] ) );
        }
        for( int i = start_offset + left_len; i < start_offset + left_len + mid_len; i++ )
        {
            mid_ampl = Math.max( mid_ampl, Math.abs( samples[ i ] ) );
        }
        for( int i = start_offset + left_len + mid_len; i < start_offset + left_len + mid_len + right_len; i++ )
        {
            right_ampl = Math.max( right_ampl, Math.abs( samples[ i ] ) );
        }
        return Math.max( mid_ampl / left_ampl, mid_ampl / right_ampl );
    }

    public void setCompare_with_direct_repair( boolean compare_with_direct_repair )
    {
        this.compare_with_direct_repair = compare_with_direct_repair;
    }

    public void setFetch_ratio( int fetch_ratio )
    {
        this.fetch_ratio = fetch_ratio;
    }

    public void setpeak_threshold( float peak_threshold )
    {
        this.peak_threshold = peak_threshold;
    }
}
