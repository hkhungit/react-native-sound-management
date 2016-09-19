//
//  SoundRecorderModule.m
//  ReactNativeSoundManagement
//
//  Created by Jim Fwz on 9/16/16.
//  Copyright © 2016 Facebook. All rights reserved.
//
//  Licensed under the MIT license. For more information, see LICENSE.

#import "SoundRecorderModule.h"
#import "RCTEventDispatcher.h"
#import "SoundHelpers.h"

@import AVFoundation;

@interface SoundRecorderModule () <AVAudioRecorderDelegate>

@property (nonatomic, strong) NSMutableDictionary *recorderPool;
@property (nonatomic, strong) AVAudioRecorder *mAudioRecorder;


@end

@implementation SoundRecorderModule{
    NSNumber *_audioRecorderId;
    id _progressUpdateTimer;
    int _progressUpdateInterval;
    NSDate *_prevProgressUpdateTime;
}

@synthesize bridge = _bridge;

- (void)dealloc {
    AVAudioSession *audioSession = [AVAudioSession sharedInstance];
    NSError *error = nil;
    [audioSession setActive:NO error:&error];
    
    if (error) {
        NSLog (@"RCTAudioRecorder: Could not deactivate current audio session. Error: %@", error);
        return;
    }
}

- (NSMutableDictionary *) recorderPool {
    if (!_recorderPool) {
        _recorderPool = [NSMutableDictionary new];
    }
    return _recorderPool;
}

-(NSNumber *) keyForRecorder:(nonnull AVAudioRecorder*)recorder {
    return [[_recorderPool allKeysForObject:recorder] firstObject];
}

#pragma mark - React exposed functions

RCT_EXPORT_MODULE();

RCT_EXPORT_METHOD(prepare:(NSString * _Nullable)filename
                  withOptions:(NSDictionary *)options
                  withCallback:(RCTResponseSenderBlock)callback)
{
    if ([filename length] == 0) {
        NSDictionary* dict = [SoundHelpers errObjWithCode:@"invalidpath"
                                         withMessage:@"Provided path was empty"];
        callback(@[dict]);
        return;
    }
    
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString *documentsDirectory = [paths objectAtIndex:0];
    NSString *filePath = [documentsDirectory stringByAppendingPathComponent:filename];
    
    NSURL *url = [NSURL fileURLWithPath:filePath];
    
    // Initialize audio session
    AVAudioSession *audioSession = [AVAudioSession sharedInstance];
    NSError *error = nil;
    [audioSession setCategory:AVAudioSessionCategoryRecord error:&error];
    if (error) {
        NSDictionary* dict = [SoundHelpers errObjWithCode:@"preparefail" withMessage:@"Failed to set audio session category"];
        callback(@[dict]);
        
        return;
    }
    
    // Set audio session active
    [audioSession setActive:YES error:&error];
    if (error) {
        NSString *errMsg = [NSString stringWithFormat:@"Could not set audio session active, error: %@", error];
        NSDictionary* dict = [SoundHelpers errObjWithCode:@"preparefail"
                                         withMessage:errMsg];
        callback(@[dict]);
        
        return;
    }
    
    // Settings for the recorder
    NSDictionary *recordSetting = [SoundHelpers recorderSettingsFromOptions:options];
    
    // Initialize a new recorder
    self.mAudioRecorder = [[AVAudioRecorder alloc] initWithURL:url settings:recordSetting error:&error];
    if (error) {
        NSString *errMsg = [NSString stringWithFormat:@"Failed to initialize recorder, error: %@", error];
        NSDictionary* dict = [SoundHelpers errObjWithCode:@"preparefail"
                                         withMessage:errMsg];
        callback(@[dict]);
        return;
        
    } else if (!self.mAudioRecorder) {
        NSDictionary* dict = [SoundHelpers errObjWithCode:@"preparefail" withMessage:@"Failed to initialize recorder"];
        callback(@[dict]);
        
        return;
    }
    self.mAudioRecorder.delegate = self;
    
    BOOL success = [self.mAudioRecorder prepareToRecord];
    if (!success) {
        NSDictionary* dict = [SoundHelpers errObjWithCode:@"preparefail" withMessage:@"Failed to prepare recorder. Settings\
                              are probably wrong."];
        callback(@[dict]);
        return;
    }
    [self stopProgressTimer];
    callback(@[[NSNull null],@{@"filepath": filePath}]);
}

RCT_EXPORT_METHOD(record:(RCTResponseSenderBlock)callback) {
    
    if (self.mAudioRecorder) {
        if (![self.mAudioRecorder record]) {
            NSDictionary* dict = [SoundHelpers errObjWithCode:@"startfail" withMessage:@"Failed to start recorder"];
            callback(@[dict]);
            return;
        }
        
        [self startProgressTimer];
    } else {
        NSDictionary* dict = [SoundHelpers errObjWithCode:@"notfound" withMessage:@"Recorder with that id was not found"];
        callback(@[dict]);
        return;
    }
    callback(@[[NSNull null]]);
}

RCT_EXPORT_METHOD(stop:(RCTResponseSenderBlock)callback) {
    if (self.mAudioRecorder) {
        [self.mAudioRecorder stop];
    } else {
        NSDictionary* dict = [SoundHelpers errObjWithCode:@"notfound" withMessage:@"Recorder with that id was not found"];
        callback(@[dict]);
        return;
    }
    [self stopProgressTimer];
    callback(@[[NSNull null]]);
}

RCT_EXPORT_METHOD(destroy:(RCTResponseSenderBlock)callback) {
    [self destroyRecorder];
    [self stopProgressTimer];
    callback(@[[NSNull null]]);
}

#pragma mark - Delegate methods
- (void)audioRecorderDidFinishRecording:(AVAudioRecorder *) aRecorder successfully:(BOOL)flag {
    if ([[_recorderPool allValues] containsObject:aRecorder]) {
        NSNumber *recordId = [self keyForRecorder:aRecorder];
        [self destroyRecorder];
    }
}

- (void)destroyRecorder {
    if (self.mAudioRecorder) {
        [self.mAudioRecorder stop];
        NSString *eventName = [NSString stringWithFormat:@"RCTSoundRecorderModuleBridgeEvent"];
        [self.bridge.eventDispatcher sendAppEventWithName:eventName
                                                     body:@{@"event" : @"ended",
                                                            @"data" : [NSNull null]
                                                            }];
    }
}

- (void)audioRecorderEncodeErrorDidOccur:(AVAudioRecorder *)recorder
                                   error:(NSError *)error {
    [self destroyRecorder];
    NSString *eventName = [NSString stringWithFormat:@"RCTSoundRecorderModuleBridgeEvent"];
    [self.bridge.eventDispatcher sendAppEventWithName:eventName
                                                 body:@{@"event": @"error",
                                                        @"data" : [error description]
                                                        }];
}

- (void)sendProgressUpdate {
    AVAudioRecorder *recorder = self.mAudioRecorder;
    if (recorder && recorder.recording)
    {
        if (_prevProgressUpdateTime == nil ||
            (([_prevProgressUpdateTime timeIntervalSinceNow] * -1000.0) >= _progressUpdateInterval)) {
            NSString *eventName = [NSString stringWithFormat:@"RCTSoundRecorderModuleBridgeEvent"];
            NSMutableDictionary *data = [[NSMutableDictionary alloc] init];
            [data setObject:[NSNumber numberWithFloat: recorder.currentTime * 1000] forKey:@"position"];
            [self.bridge.eventDispatcher sendAppEventWithName:eventName
                                                         body:@{@"event": @"progress",
                                                                @"data" : data
                                                                }];
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

@end
