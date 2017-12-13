package AudioDataSource.VersionedAudioDataSource;

import AudioDataSource.IAudioDataSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Alex on 12.12.2017.
 */
public class VersionedAudioDataSource
{
    private List<AudioDataSourceVersion> versions = new ArrayList<>();
    private int current_version = 0;
}
