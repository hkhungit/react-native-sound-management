
//  Licensed under the MIT license. For more information, see LICENSE.

#import "SoundPlayerModule.h"
#import "SoundHelpers.h"
#import "RCTEventDispatcher.h"
#import "RCTUtils.h"

@implementation SoundPlayerModule {
    id _progressUpdateTimer;
    int _progressUpdateInterval;
    NSDate *_prevProgressUpdateTime;
}

@synthesize bridge = _bridge;

- (void)dealloc {
    [self destroyPlayer];
}

- (NSURL *)findUrlForPath:(NSString *)path {
    NSURL *url = nil;
    
    NSArray *pathComponents = [NSArray arrayWithObjects:
                               [NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) lastObject],
                               path,
                               nil];
    
    NSString *possibleUrl = [NSString pathWithComponents:pathComponents];
    
    if (![[NSFileManager defaultManager] fileExistsAtPath:possibleUrl]) {
        NSString *fileWithoutExtension = [path stringByDeletingPathExtension];
        NSString *extension = [path pathExtension];
        NSString *urlString = [[NSBundle mainBundle] pathForResource:fileWithoutExtension ofType:extension];
        if (urlString) {
            url = [NSURL fileURLWithPath:urlString];
        } else {
            NSString* mainBundle = [NSString stringWithFormat:@"%@/%@", [[NSBundle mainBundle] bundlePath], path];
            BOOL isDir = NO;
            NSFileManager* fm = [NSFileManager defaultManager];
            if ([fm fileExistsAtPath:mainBundle isDirectory:&isDir]) {
                url = [NSURL fileURLWithPath:mainBundle];
            } else {
                url = [NSURL URLWithString:path];
            }
            
        }
    } else {
        url = [NSURL fileURLWithPathComponents:pathComponents];
    }
    
    return url;
}

#pragma mark React exposed methods

RCT_EXPORT_MODULE();

// This method initializes and prepares the player
RCT_EXPORT_METHOD(setUrl:(NSString* _Nullable)path
                  withCallback:(RCTResponseSenderBlock)callback)
{
    [self prepare:path withCallback:callback];
}

// This method initializes and prepares the player
RCT_EXPORT_METHOD(prepare:(NSString* _Nullable)path
                  withCallback:(RCTResponseSenderBlock)callback)
{
    if ([path length] == 0) {
        NSDictionary* dict = [SoundHelpers errObjWithCode:@"invalidpath" withMessage:@"Provided path was empty"];
        callback(@[dict]);
        return;
    }
    
    // Try to find the correct file
    NSURL *url = [self findUrlForPath:path];
    if (!url) {
        NSDictionary* dict = [SoundHelpers errObjWithCode:@"invalidpath" withMessage:@"No file found at path"];
        callback(@[dict]);
        return;
    }
    
    // Load asset from the url
    AVURLAsset *asset = [AVURLAsset assetWithURL: url];
    AVPlayerItem *item = (AVPlayerItem *)[AVPlayerItem playerItemWithAsset: asset];
    
    // Add notification to know when file has stopped playing
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(itemDidFinishPlaying:)
                                                 name:AVPlayerItemDidPlayToEndTimeNotification
                                               object:item];
    
    // Set audio session
    NSError *error = nil;
    [[AVAudioSession sharedInstance] setCategory: AVAudioSessionCategoryPlayback error: &error];
    if (error) {
        NSDictionary* dict = [SoundHelpers errObjWithCode:@"preparefail"
                                         withMessage:@"Failed to set audio session category."];
        callback(@[dict]);
        return;
    }
    
    // Initialize player
    self.mAVPlayer = [[AVPlayer alloc]
                      initWithPlayerItem:item];
    
    // Prepare the player
    // Wait until player is ready
    while (self.mAVPlayer.status == AVPlayerStatusUnknown) {
        [NSThread sleepForTimeInterval:0.01f];
    }
    
    // Callback when ready / failed
    if (self.mAVPlayer.status == AVPlayerStatusReadyToPlay) {
        callback(@[[NSNull null]]);
    } else {
        NSDictionary* dict = [SoundHelpers errObjWithCode:@"preparefail"
                                         withMessage:[NSString stringWithFormat:@"Preparing player failed"]];
        callback(@[dict]);
    }
}

RCT_EXPORT_METHOD(destroy:(RCTResponseSenderBlock)callback) {
    [self destroyPlayer];
    callback(@[[NSNull null]]);
}

RCT_EXPORT_METHOD(seek:(nonnull NSNumber*)position withCallback:(RCTResponseSenderBlock)callback) {
    if (!self.mAVPlayer) {
        NSDictionary* dict = [SoundHelpers errObjWithCode:@"notfound"
                                         withMessage:[NSString stringWithFormat:@"player not found."]];
        callback(@[dict]);
        return;
    }
    
    [self.mAVPlayer cancelPendingPrerolls];
    
    if (position >= 0) {
        NSLog(@"%@", position);
        if (position == 0) {
            [self.mAVPlayer.currentItem
             seekToTime:kCMTimeZero
             toleranceBefore:kCMTimeZero // for precise positioning
             toleranceAfter:kCMTimeZero
             completionHandler:^(BOOL finished) {
                 callback(@[[NSNull null], [self timeAVPlayer]]);
             }];
        } else {
            [self.mAVPlayer.currentItem
             seekToTime:CMTimeMakeWithSeconds([position doubleValue] / 1000, 60000)
             completionHandler:^(BOOL finished) {
                 callback(@[[NSNull null],[self timeAVPlayer]]);
             }];
        }
    }
}

RCT_EXPORT_METHOD(play:(RCTResponseSenderBlock)callback) {
    if (!self.mAVPlayer) {
        NSDictionary* dict = [SoundHelpers errObjWithCode:@"notfound"
                                         withMessage:[NSString stringWithFormat:@"AVPlayer not found."]];
        callback(@[dict]);
        return;
    }
    
    [self.mAVPlayer play];
    [self startProgressTimer];
    callback(@[[NSNull null], [self timeAVPlayer]]);
}

RCT_EXPORT_METHOD(set:(NSDictionary*)options withCallback:(RCTResponseSenderBlock)callback) {
    
    if (!self.mAVPlayer) {
        NSDictionary* dict = [SoundHelpers errObjWithCode:@"notfound"
                                         withMessage:[NSString stringWithFormat:@"AVPlayer not found."]];
        callback(@[dict]);
        return;
    }
    
    NSNumber *volume = [options objectForKey:@"volume"];
    if (volume) {
        [self.mAVPlayer setVolume:[volume floatValue]];
    }
    
    NSNumber *looping = [options objectForKey:@"looping"];
    if (looping) {
        self.looping = [looping boolValue];
    }
    
    callback(@[[NSNull null]]);
}

RCT_EXPORT_METHOD(stop:(RCTResponseSenderBlock)callback) {
    
    if (!self.mAVPlayer) {
        NSDictionary* dict = [SoundHelpers errObjWithCode:@"notfound"
                                         withMessage:[NSString stringWithFormat:@"AVPlayer not found."]];
        callback(@[dict]);
        return;
    }
    
    [self.mAVPlayer pause];
    [self.mAVPlayer.currentItem seekToTime:CMTimeMakeWithSeconds(0.0, 60000)];
    
    callback(@[[NSNull null], [self timeAVPlayer]]);
}

RCT_EXPORT_METHOD(pause:(RCTResponseSenderBlock)callback) {
    
    if (!self.mAVPlayer) {
        NSDictionary* dict = [SoundHelpers errObjWithCode:@"notfound"
                                         withMessage:[NSString stringWithFormat:@"AVPlayer not found."]];
        callback(@[dict]);
        return;
    }
    
    [self.mAVPlayer pause];
    
    callback(@[[NSNull null], [self timeAVPlayer]]);
}

RCT_EXPORT_METHOD(resume:(nonnull NSNumber*)playerId withCallback:(RCTResponseSenderBlock)callback) {
    if (!self.mAVPlayer) {
        NSDictionary* dict = [SoundHelpers errObjWithCode:@"notfound"
                                         withMessage:[NSString stringWithFormat:@"AVPlayer not found."]];
        callback(@[dict]);
        return;
    }
    
    [self.mAVPlayer play];
    [self startProgressTimer];
    callback(@[[NSNull null]]);
    [self emitEvent:@"resume" withOptions: [NSNull null]];
}


-(void)itemDidFinishPlaying:(NSNotification *) notification {
    [self seek:@0 withCallback:^(NSArray *response) {
        return;
    }];
    if (self.looping && self.mAVPlayer) {
        [self.mAVPlayer play];
        [self startProgressTimer];
        [self emitEvent:@"looped" withOptions: [NSNull null]];
    } else {
        [self emitEvent:@"ended" withOptions: [NSNull null]];
    }
}

- (void)destroyPlayer{
    if (self.mAVPlayer) {
        [self.mAVPlayer pause];
    }
}

- (void)sendProgressUpdate {
    if (self.mAVPlayer)
    {
        if (_prevProgressUpdateTime == nil ||
            (([_prevProgressUpdateTime timeIntervalSinceNow] * -1000.0) >= _progressUpdateInterval)) {
            [self emitEvent:@"progress" withOptions: [self timeAVPlayer]];
            _prevProgressUpdateTime = [NSDate date];
        }
    }
}

- (void)stopProgressTimer {
    [_progressUpdateTimer invalidate];
}

- (void)startProgressTimer {
    _progressUpdateInterval = 100;
    _prevProgressUpdateTime = nil;
    [self stopProgressTimer];
    
    _progressUpdateTimer = [CADisplayLink displayLinkWithTarget:self selector:@selector(sendProgressUpdate)];
    [_progressUpdateTimer addToRunLoop:[NSRunLoop mainRunLoop] forMode:NSDefaultRunLoopMode];
}

- (void) emitEvent:(NSString *)event withOptions:(NSMutableDictionary *) data
{
    NSString *eventName = [NSString stringWithFormat:@"RCTSoundPlayerModuleBridgeEvent"];
    [self.bridge.eventDispatcher sendAppEventWithName:eventName
                                                 body:@{@"event": event, @"data": data}];
}

-(NSMutableDictionary *) timeAVPlayer {
    NSMutableDictionary *dicts = [[NSMutableDictionary alloc] init];
    if (self.mAVPlayer != nil)
    {
        [dicts setValue:  @(CMTimeGetSeconds(self.mAVPlayer.currentTime) * 1000) forKey: @"position"];
        [dicts setValue: @(CMTimeGetSeconds(self.mAVPlayer.currentItem.asset.duration) * 1000) forKey: @"duration"];
    }
    return  dicts;
}
@end
