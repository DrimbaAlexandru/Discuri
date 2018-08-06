package SignalProcessing.Effects;

import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.IAudioDataSource;
import AudioDataSource.MemoryADS.InMemoryADS;
import AudioDataSource.MemoryADS.SingleBlockADS;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import MarkerFile.Marking;
import ProjectManager.ProjectManager;
import SignalProcessing.Filters.FIR;
import SignalProcessing.FourierTransforms.Fourier;
import Utils.Interval;
import Utils.Util_Stuff;

import java.util.*;

/**
 * Created by Alex on 15.06.2018.
 */
public class Multi_Band_Repair_Marked implements IEffect
{
    final private int band_pass_filter_length;
    private ArrayList< Integer > band_cutoffs = new ArrayList<>();
    final private int max_repair_size;
    private int fetch_ratio;
    private int buffer_size;
    private boolean repair_residue = false;
    private double peak_threshold = 4;
    private boolean compare_with_direct_repair = false;
    private double progress = 0;

    public Multi_Band_Repair_Marked()
    {
        this( 511, 256, 16 );
    }

    public Multi_Band_Repair_Marked( int band_pass_filter_length, int max_repaired_size, int repair_fetch_ratio )
    {
        this.band_pass_filter_length = band_pass_filter_length;
        max_repair_size = max_repaired_size;
        fetch_ratio = repair_fetch_ratio;
        buffer_size = max_repair_size * ( fetch_ratio * 2 + 1 ) + band_pass_filter_length;
    }

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        if( dataSource == dataDest )
        {
            throw new DataSourceException( "Data Source and Data Dest cannot be the same", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        progress = 0;
        band_cutoffs.sort( Comparator.reverseOrder() );
        /*
            Local variables.
            Filter definitions
        */
        final int nr_of_bands = band_cutoffs.size();
        final FIR[] filters = new FIR[ nr_of_bands ];
        final int nr_freqs = dataSource.get_sample_rate() / 2 / 50;
        final double[] freqs = new double[ nr_freqs ];
        final double[] bp_response = new double[ nr_freqs ]; //this is in dB
        final double[] prev_response = new double[ nr_freqs ]; //this is linear

        for( int i = 0; i < nr_freqs; i++ )
        {
            freqs[ i ] = 1.0 * i * dataSource.get_sample_rate() / 2 / nr_freqs;
        }
        Arrays.setAll( prev_response, x -> 1 );

        for( int f = 0; f < nr_of_bands; f++ )
        {
            Arrays.setAll( bp_response, x -> 0 );
            FIR.add_pass_cut_freq_resp( freqs, bp_response, nr_freqs, band_cutoffs.get( f ), -96, 0 );

            Util_Stuff.dB2lin( bp_response, nr_freqs );
            for( int i = 0; i < nr_freqs; i++ )
            {
                prev_response[ i ] = bp_response[ i ] *= prev_response[ i ];
            }
            Util_Stuff.lin2dB( bp_response, nr_freqs );

            filters[ f ] = FIR.fromFreqResponse( freqs, bp_response, nr_freqs, dataSource.get_sample_rate(), band_pass_filter_length );

        }

        /*
            Data sources
        */
        final SingleBlockADS repair_dest = new SingleBlockADS( dataSource.get_sample_rate(), dataSource.get_channel_number(), 0, new Interval( 0, 0 ) );
        //workDS[nr_of_bands] - buffer-ul pentru reziduu
        final InMemoryADS workDS[] = new InMemoryADS[ nr_of_bands + 1 ];
        for( int i = 0; i < nr_of_bands + 1; i++ )
        {
            workDS[ i ] = new InMemoryADS( buffer_size, dataSource.get_channel_number(), dataSource.get_sample_rate() );
        }

        AudioSamplesWindow requested_repaired, repaired;

        /*
        * Effects
        */
        final Equalizer equalizer = new Equalizer();
        final Repair_One repairOne = new Repair_One();
        repairOne.set_fetch_ratio( fetch_ratio );
        /*
            Indexes and other shit
        */
        int i;
        int marking_index = 0;
        //side length e numarul de sample-uri necesar la dreapta si la stanga selectiei pentru repair, necesare la band-pass
        final int side_length = band_pass_filter_length / 2;
        boolean repair_direct;
        Random random = new Random();

        /*
            Work
        */
        interval.limit( 0, dataSource.get_sample_number() );
        if( !repair_residue && nr_of_bands == 0 && !compare_with_direct_repair )
        {
            return;
        }
        List< Marking > orig_repair_intervals = ProjectManager.getMarkerFile().get_all_markings( interval );
        List< Marking > repair_intervals = new ArrayList<>();

        for( Marking m : orig_repair_intervals )
        {
            if( m.get_number_of_marked_samples() > max_repair_size )
            {
                int remaining_length = m.get_number_of_marked_samples();
                while( remaining_length > 0 )
                {
                    int new_len = ( remaining_length < max_repair_size / 2 ) ? remaining_length : ( ( int )( max_repair_size / 2 * ( 2.5f + random.nextFloat() ) / 3 ) );
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

        repair_intervals.sort( ( i1, i2 ) ->
                               {
                                   return -( i2.getInterval().l - i2.getInterval().get_length() * fetch_ratio ) + ( i1.getInterval().l - i1.getInterval().get_length() * fetch_ratio );
                               } );

        for( Marking marking : repair_intervals )
        {
            Interval repair_interval = marking.getInterval();
            try
            {
                Interval interval_required_for_prediction = new Interval( repair_interval.l - fetch_ratio * repair_interval.get_length(), repair_interval.r + fetch_ratio * repair_interval.get_length(), false );
                repairOne.setAffected_channels( Arrays.asList( marking.getChannel() ) );

                if( interval_required_for_prediction.l < 0 || interval_required_for_prediction.r > dataSource.get_sample_number() )
                {
                    throw new DataSourceException( "Not enough samples for linear prediction", DataSourceExceptionCause.INTERPOLATION_EXCEPTION );
                }
                if( repair_interval.get_length() > max_repair_size )
                {
                    throw new DataSourceException( "Repair interval too big", DataSourceExceptionCause.WARNING );
                }

                repair_direct = repair_residue && ( nr_of_bands == 0 );
                if( !repair_direct && compare_with_direct_repair )
                {
                    int rep_len = repair_interval.get_length();
                    int spike_det_left_len = Math.min( band_pass_filter_length / 3, repair_interval.l );
                    int spike_det_right_len = Math.min( band_pass_filter_length / 3, dataSource.get_sample_number() - repair_interval.r );
                    AudioSamplesWindow from_source = dataSource.get_samples( repair_interval.l - spike_det_left_len, rep_len + spike_det_left_len + spike_det_right_len );
                    double spike_ratio = get_freq_spike( from_source.getSamples()[ marking.getChannel() ], 0, spike_det_left_len, rep_len, spike_det_right_len );
                    repair_direct = ( spike_ratio > peak_threshold );
                    if( repair_direct )
                    {
                        System.out.println( "Direct repair at on interval " + marking + " with ratio = " + spike_ratio );
                    }
                }

                //Calculate the requested repair OR the direct repair
                if( !repair_direct )
                {
                    //Make sure all the required samples are in workADSs
                    if( !workDS[ 0 ].get_buffer_interval().includes( interval_required_for_prediction ) )
                    {

                        //shift left as much as possible and as rare as possible
                        //if( workDS[ 0 ].get_buffer_interval().r < interval_required_for_prediction.r || workDS[ 0 ].get_buffer_interval().l + workDS[ 0 ].getCapacity() < interval_required_for_prediction.r )
                        {
                            int amount_to_shift = interval_required_for_prediction.l - workDS[ 0 ].get_buffer_interval().l;
                            for( i = 0; i <= nr_of_bands; i++ )
                            {
                                workDS[ i ].shift_interval( amount_to_shift );
                            }
                        }
                        Interval not_existing = new Interval( workDS[ 0 ].get_buffer_interval().r, interval_required_for_prediction.r, false );
                        not_existing.limit( interval_required_for_prediction.l, interval_required_for_prediction.r );
                        for( i = 0; i < nr_of_bands; i++ )
                        {
                            equalizer.setFilter( filters[ i ] );
                            equalizer.apply( dataSource, workDS[ i ], not_existing );
                        }
                        AudioSamplesWindow win = dataSource.get_samples( not_existing.l, not_existing.get_length() );
                        for( i = 0; i < nr_of_bands; i++ )
                        {
                            AudioSamplesWindow winb = workDS[ i ].get_samples( not_existing.l, not_existing.get_length() );
                            for( int k = 0; k < win.get_channel_number(); k++ )
                            {
                                for( int j = not_existing.l; j < not_existing.r; j++ )
                                {
                                    win.putSample( j, k, win.getSample( j, k ) - winb.getSample( j, k ) );
                                }
                            }
                        }
                        workDS[ nr_of_bands ].put_samples( win );
                    /*for( i = 0; i <= nr_of_bands; i++ )
                    {
                        win = workDS[ i ].get_samples( workDS[ i ].get_buffer_interval().l, workDS[ i ].get_buffer_interval().get_length() );
                        Util_Stuff.plot_in_matlab( win.getSamples()[ 0 ], win.get_length() );
                        System.out.println( "----" );
                    }*/
                    }

                    //Begin the repair process
                    if( repair_residue )
                    {
                        repairOne.apply( workDS[ nr_of_bands ], repair_dest, repair_interval );
                        requested_repaired = repair_dest.get_samples( repair_interval.l, repair_interval.get_length() );
                    }
                    else
                    {
                        requested_repaired = workDS[ nr_of_bands ].get_samples( repair_interval.l, repair_interval.get_length() );
                    }
                    //Util_Stuff.plot_in_matlab( repaired_samples, repair_interval.get_length() );
                    //System.out.println( "----" );
                    for( i = 0; i < nr_of_bands; i++ )
                    {
                        repairOne.apply( workDS[ i ], repair_dest, repair_interval );
                        repaired = repair_dest.get_samples( repair_interval.l, repair_interval.get_length() );
                        for( int j = repair_interval.l; j < repair_interval.r; j++ )
                        {
                            requested_repaired.getSamples()[ marking.getChannel() ][ j - repair_interval.l ] += repaired.getSample( j, marking.getChannel() );
                        }
                        //Util_Stuff.plot_in_matlab( repaired_samples, repair_interval.get_length() );
                        //System.out.println( "----" );
                    }
                    repaired = dataDest.get_samples( repair_interval.l, repair_interval.get_length() );
                    repaired.getSamples()[ marking.getChannel() ] = requested_repaired.getSamples()[ marking.getChannel() ];
                    dataDest.put_samples( repaired );
                }
                else
                {
                    repaired = dataDest.get_samples( repair_interval.l, repair_interval.get_length() );
                    repairOne.apply( dataSource, repair_dest, repair_interval );
                    AudioSamplesWindow direct_repaired = repair_dest.get_samples( repair_interval.l, repair_interval.get_length() );
                    repaired.getSamples()[ marking.getChannel() ] = direct_repaired.getSamples()[ marking.getChannel() ];
                    dataDest.put_samples( repaired );
                }
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
            progress = 1.0 * marking_index / repair_intervals.size();
        }
    }

    @Override
    public double getProgress()
    {
        return progress;
    }

    public ArrayList< Integer > getBand_cutoffs()
    {
        return band_cutoffs;
    }

    public void setRepair_residue( boolean repair_residue )
    {
        this.repair_residue = repair_residue;
    }

    private double get_freq_spike( double[] samples, int start_offset, int left_len, int mid_len, int right_len )
    {
        double left_ampl = 0, mid_ampl = 0, right_ampl = 0;
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
        buffer_size = max_repair_size * ( fetch_ratio * 2 + 1 ) + band_pass_filter_length;
    }

    public void setpeak_threshold( double peak_threshold )
    {
        this.peak_threshold = peak_threshold;
    }
}
