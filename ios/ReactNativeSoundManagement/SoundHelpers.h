//
//  Helpers.h
//  ReactNativeSoundManagement
//
//  Created by Jim Fwz on 9/16/16.
//  Copyright © 2016 Facebook. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface SoundHelpers : NSObject

+(NSDictionary *) errObjWithCode:(NSString*)code
                     withMessage:(NSString*)message;

+(NSDictionary *)recorderSettingsFromOptions:(NSDictionary *)options;

@end
