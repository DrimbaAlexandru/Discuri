package SignalProcessing.Effects;

import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.ADCache.CachedAudioDataSource;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import AudioDataSource.FileADS.AUFileAudioSource;
import AudioDataSource.IAudioDataSource;
import AudioDataSource.ADS_Utils;
import ProjectStatics.ProjectStatics;
import SignalProcessing.Filters.FIR;
import SignalProcessing.Filters.IIR;
import SignalProcessing.Windowing.Windowing;
import Utils.Interval;

import java.util.function.Function;

/**
 * Created by Alex on 08.02.2018.
 */
public class Repair_in_high_pass implements IEffect
{
    final private int file_byte_depth = 4;
    final private int cutoff_frq = 2000;
    final private double preamp = Math.pow( 2, -30.0 / 6 );
    final private double postamp = Math.pow( 2, 30.0 / 6 );
    final private int chunk_size = 1024 * 1024;
    final private int riaa_length = 2047;
    final private int low_pass_length = 511;

    @Override
    public String getName()
    {
        return "Repair in high band of position domain";
    }

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        final double preamp_coeffs[] = new double[]{ preamp };
        final double postamp_coeffs[] = new double[]{ postamp };
        final double riaa_response[] = FIR.get_RIAA_response( riaa_length, dataSource.get_sample_rate() );
        final double inverse_riaa_response[] = FIR.get_inverse_RIAA_response( riaa_length, dataSource.get_sample_rate() );
        final double low_pass_resp[] = FIR.pass_cut_freq_resp( low_pass_length, cutoff_frq, dataSource.get_sample_rate(), 0, -96 );
        final double integration_coeffs[] = { 1, -1 };
        Function< Double, Double > window = Windowing.Blackman_window;

        FIR derivation_filter = new FIR( new double[]{ 1, -1 }, 2 );
        FIR riaa_filter = FIR.fromFreqResponse( riaa_response, riaa_length, dataSource.get_sample_rate(), riaa_length );
        FIR inverse_riaa_filter = FIR.fromFreqResponse( inverse_riaa_response, riaa_length, dataSource.get_sample_rate(), riaa_length );
        FIR low_pass_filter = FIR.fromFreqResponse( low_pass_resp, low_pass_length, dataSource.get_sample_rate(), low_pass_length );
        IIR integration_filter = new IIR( inverse_riaa_filter.getFf(), inverse_riaa_filter.getFf_coeff_nr(), integration_coeffs, integration_coeffs.length );

        Windowing.apply( integration_filter.getFf(), integration_filter.getFf_coeff_nr(), ( v ) -> preamp );
        Windowing.apply( riaa_filter.getFf(), riaa_filter.getFf_coeff_nr(), ( v ) -> postamp );
        //Windowing.apply( low_pass_filter.getFf(), low_pass_filter.getFf_coeff_nr(), ( v ) -> postamp );

        Windowing.apply( integration_filter.getFf(), integration_filter.getFf_coeff_nr(), window );
        Windowing.apply( riaa_filter.getFf(), riaa_filter.getFf_coeff_nr(), window );
        Windowing.apply( low_pass_filter.getFf(), low_pass_filter.getFf_coeff_nr(), window );

        AUFileAudioSource temp_file1, temp_file2, temp_file3;
        CachedAudioDataSource temp_cache1, temp_cache2, temp_cache3;

        temp_file1 = new AUFileAudioSource( ProjectStatics.getTemp_folder() + "temp1.au", dataSource.get_channel_number(), dataSource.get_sample_rate(), file_byte_depth );
        temp_cache1 = new CachedAudioDataSource( temp_file1, ProjectStatics.getDefault_cache_size(), ProjectStatics.getDefault_cache_page_size() );
        temp_file2 = new AUFileAudioSource( ProjectStatics.getTemp_folder() + "temp2.au", dataSource.get_channel_number(), dataSource.get_sample_rate(), file_byte_depth );
        temp_cache2 = new CachedAudioDataSource( temp_file2, ProjectStatics.getDefault_cache_size(), ProjectStatics.getDefault_cache_page_size() );
        temp_file3 = new AUFileAudioSource( ProjectStatics.getTemp_folder() + "temp3.au", dataSource.get_channel_number(), dataSource.get_sample_rate(), file_byte_depth );
        temp_cache3 = new CachedAudioDataSource( temp_file3, ProjectStatics.getDefault_cache_size(), ProjectStatics.getDefault_cache_page_size() );

        FIR_Filter fir_filter = new FIR_Filter();
        Equalizer equalizer = new Equalizer();
        IIR_with_centered_FIR iir_with_centered_fir = new IIR_with_centered_FIR();

        if( interval.l < 0 )
        {
            interval.l = 0;
        }
        if( interval.r > dataSource.get_sample_number() )
        {
            interval.r = dataSource.get_sample_number();
        }

        fir_filter.setMax_chunk_size( chunk_size );
        equalizer.setMax_chunk_size( chunk_size );
        iir_with_centered_fir.setMax_chunk_size( chunk_size );

        //Amplificare -20dB + inverse RIAA + Integrare
        iir_with_centered_fir.setFilter( integration_filter );
        iir_with_centered_fir.apply( dataSource, temp_cache1, interval );
        System.out.println( "Finished phases 1, 2 & 3" );

        //Separare low-pass
        equalizer.setFilter( low_pass_filter );
        equalizer.apply( temp_cache1, temp_cache2, interval );

        temp_cache1.flushAll();
        System.out.println( "Finished phase 4a" );

        int i;
        int temp_len, k, j;
        AudioSamplesWindow win1, win2;

        //Separare high-pass
        i = interval.l;
        while( i < interval.r )
        {
            temp_len = Math.min( chunk_size, interval.r - i );
            win1 = temp_cache1.get_samples( i, temp_len );
            win2 = temp_cache2.get_samples( i, temp_len );
            temp_len = win1.get_length();
            if( temp_len == 0 || win1.get_length() != win2.get_length() )
            {
                throw new DataSourceException( "Got wrong length window", DataSourceExceptionCause.GENERIC_ERROR );
            }
            for( k = 0; k < dataSource.get_channel_number(); k++ )
            {
                for( j = win1.get_first_sample_index(); j < win1.get_after_last_sample_index(); j++ )
                {
                    win1.putSample( j, k, win1.getSample( j, k ) - win2.getSample( j, k ) );
                }
            }
            temp_cache1.put_samples( win1 );
            i += temp_len;
        }
        System.out.println( "Finished phase 4b" );

        //Reparare in high-pass
        ADS_Utils.copyToADS( temp_cache1, temp_cache3 );
        Repair_Marked repair_marked = new Repair_Marked();
        repair_marked.apply( temp_cache1, temp_cache3, interval );
        System.out.println( "Finished phase 5" );
        //TODO: Delete temp file 1

        //Combinare high-pass / low-pass
        i = interval.l;
        while( i < interval.r )
        {
            temp_len = Math.min( chunk_size, interval.r - i );
            win1 = temp_cache3.get_samples( i, temp_len );
            win2 = temp_cache2.get_samples( i, temp_len );
            temp_len = win1.get_length();
            if( temp_len == 0 || win1.get_length() != win2.get_length() )
            {
                throw new DataSourceException( "Got wrong length window", DataSourceExceptionCause.GENERIC_ERROR );
            }
            for( k = 0; k < dataSource.get_channel_number(); k++ )
            {
                for( j = win1.get_first_sample_index(); j < win1.get_after_last_sample_index(); j++ )
                {
                    win1.putSample( j, k, win1.getSample( j, k ) + win2.getSample( j, k ) );
                }
            }
            temp_cache2.put_samples( win1 );
            i += temp_len;
        }
        System.out.println( "Finished phase 6" );
        //TODO: Delete temp file 3

        //Derivare discreta
        fir_filter.setFilter( derivation_filter );
        fir_filter.apply( temp_cache2, temp_cache2, interval );
        System.out.println( "Finished phase 7" );

        //Aplicare RIAA+Amplificare +20dB
        equalizer.setFilter( riaa_filter );
        equalizer.apply( temp_cache2, dataDest, interval );
        System.out.println( "Finished phase 8 & 9" );
        //TODO: Delete temp file 2

    }
}
