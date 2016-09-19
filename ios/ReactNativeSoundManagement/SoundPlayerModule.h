#import "RCTBridgeModule.h"
#import <AVFoundation/AVFoundation.h>
#import <AVFoundation/AVPlayer.h>
#import <AVFoundation/AVPlayerItem.h>
#import <AVFoundation/AVAsset.h>

@interface SoundPlayerModule : NSObject <RCTBridgeModule>

@property (nonatomic, readwrite) BOOL isPlayingWithOthers;
@property (nonatomic, readwrite) NSString *lastUrlString;
@property (nonatomic, readwrite) NSDictionary *mMediaMeta;
@property (nonatomic, readwrite) NSString *UrlString;
@property (nonatomic, readwrite) BOOL *looping;
@property (nonatomic, readwrite) AVPlayer *mAVPlayer;
@property (nonatomic, retain) NSString *currentSong;

- (void)play;
- (void)pause;

@end
